package com.randomstatistic

import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.framework.api.{CuratorWatcher, CuratorEventType, CuratorEvent, CuratorListener}
import org.apache.zookeeper.WatchedEvent
import java.util.concurrent.{LinkedBlockingQueue, BlockingQueue}
import scala.io.Source
import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.{StandardOpenOption, Files}
import java.util.Calendar


object ZkMonitor extends App {

  type ZkPath = String
  val changes: BlockingQueue[(ZkPath, String)] = new LinkedBlockingQueue[(ZkPath, String)]()
  var gitArchive: GitArchive = null

  case class CLIConfig(
                        zk: String = "",
                        paths: List[ZkPath] = List(),
                        repo: File = new File("./zkchanges"))

  val cliParser = new scopt.OptionParser[CLIConfig]("zk_monitor") {
    help("help")text("print this usage text")
    opt[String]('z', "zk") action { (x, c) => { c.copy(zk = x) } } text("Zookeeper connection string")
    opt[String]('n', "node") action { (x, c) => c.copy(paths = x.split(",").toList) } text("ZK Nodes to monitor (comma delinated)")
    opt[String]('g', "git-repo") action { (x, c) => c.copy(repo = new File(x)) } text("Name/Path of the git repo to record changes in")
    checkConfig{
      c =>
        if (c.zk.isEmpty) failure("provide a zookeeper connection string")
        else if (c.paths.isEmpty) failure("provide one or more nodes to monitor")
        else success
    }
  }


  // intialize
  cliParser.parse(args, CLIConfig()) map { config =>
    val retryPolicy = new ExponentialBackoffRetry(1000, 5)
    val curator: CuratorFramework = CuratorFrameworkFactory.newClient(config.zk, retryPolicy)
    gitArchive = new GitArchive(config.repo)

    sys.addShutdownHook({
      println("Shutting down")
      curator.close()
    })


    curator.start()
    curator.getCuratorListenable.addListener(new Listener(changes))

    var watchers: List[Watcher] = List()
    config.paths.foreach( node => {
      println(s"Adding watcher for node $node")
      watchers = watchers :+ new Watcher(curator, node)
    })
    watchers.foreach(_.setWatch())

  }


  // runloop
  for((path, content) <- Iterator.continually(changes.take())) {
    gitArchive.addVersion(path, content)
  }


  case class Watcher(client: CuratorFramework, path: String) extends CuratorWatcher {

    def setWatch() {
      //println(s"Watcher($path) setting watch")
      client.getData.usingWatcher(this).inBackground().forPath(path)
    }
    override def process(event: WatchedEvent): Unit = {
      setWatch()
    }
  }


  class Listener(queue: BlockingQueue[(ZkPath, String)]) extends CuratorListener {
    override def eventReceived(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.WATCHED) {
        //println(s"Listener saw: ${event.getType.toString}")
      }
      else if (event.getType == CuratorEventType.GET_DATA) {
        //println(s"Listener saw: ${event.getType.toString}: ${event.getPath}")
        val (data, path) = (event.getData, event.getPath)
        val decoded = Source.fromBytes(data, "UTF8").getLines().mkString("\n")
        queue.add((path, decoded))
      }
      else {
        println(s"Listener saw: ${event.getType.toString}: $event")
      }
    }
  }

  class GitArchive(repoPath: File) {
    val repo = Git.init().setDirectory(repoPath).call

    def addVersion(filePath: String, contents: String): Unit = {
      val localPath = repoPath + "/" + filePath
      addVersion(filePath, new File(localPath), contents)
    }
    def addVersion(filePath: String, file: File, contents: String): Unit = {

      file.getParentFile.mkdirs
      val byteContents = (contents.trim + "\n").getBytes("UTF-8")
      Files.write(file.toPath, byteContents)

      if (!repo.status.call.isClean) {
        println(s"${Calendar.getInstance.getTime}: Committing a change to ${file.toPath}")
        repo.add().addFilepattern(".").call()
        repo.commit().setAuthor("zk_monitor", "null@example.com").setMessage(s"New Version of $filePath").call()
      }
    }


  }


}

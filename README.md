git_zk_monitor
==============


Monitors one or more data nodes in a ZK ensemble, and writes that data into a local git 
repo whenever those nodes change. This allows easy post-facto analysis of what changes
happened when, using normal git tools.

Note that ZK offers no absolute guarantee that a client will get every version of a node.
There's a lag between when a watch is fired, and the next watch is set, in which you could miss
a change.

Usage 
======
    sbt "run -z zkhost.example.com:2181 -n /some/data/node,/another/data/node"

    
Runs until killed with Ctrl-C. 

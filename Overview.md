# Overview of Current Files

## CASPaxos
This was a challenge; Multi-Paxos and Raft are pretty dang complicated
to implement, but Single Decree Paxos is pretty interesting. All credit
to Denis Rystov for his blog posts and eventual paper on what he called
CASPaxos. This class is a paxos framework for KV storage, users have to 
provide network and storage objects to build a node. 

## ChiseledMap
This is a very simple but pretty useful persistent ConcurrentMap 
implementation. At it's core, it has an in-memory map of keys to 
disk locations, then a append only log for storing key+value. The log
on disk never gets smaller, though you can compact it to another file.   
 
## DumbCLIParse
Oh, lord, how many times do I need a quick and dirty CLI parser to
throw together some one-off scripty Java tool. Well, here it is. 

## PojoClientServer
Client and Server objects over Java blocking sockets. Expects to send,
receive, or send-and-receive POJOs, using either java Serialization
or a provided serialization strategy.   

## ProxyMe
Proxy an interface, and then provide a midpoint in the proxy chain
where you can tap in and ship the invocation to the server site, 
and then ship the return value back and complete the operation. Stupid
prototype RPC mechanism if you couple it with some network transport
backplane.  

---
- Mar/18/2020 Initial

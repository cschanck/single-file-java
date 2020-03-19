# single-file-java
What can you do in one file, no dependencies?

Modern Java development is often a joy *because* of the extensive ecosystem. 
Lots of the time, you can easily rely on lots of third-party libraries to 
help build your masterpiece. 

This is one of the great strengths of Java, no question, and in no way would
I want to do professional Java without the ecosystem.

However, it can be instructive to look backwards, back to a time before build systems
with automatic dependencies, back before frameworks upon frameworks, back when
if you wanted something to work -- you built it yourself. 

From scratch. 

Software minimalism FTW!  

## Ground Rules
* 1000 lines in a file, no more than 120 chars on a line, commented enough 
to be useful. Yes, comments count toward you 1000 lines.
* 1 piece of functionality; i.e., collections of random misc utils don't count.
* On the other hand, feel free to think about building "mini-frameworks". For example, the 
CASPaxos implementation delegates all of the network and storage functionality 
to some interfaces a user would have to provide; network transport and 
key-value persistence are not actually part of the CASPaxos remit. 
* **No dependencies** beyond test code deps for your single test file.
   * there is some thought that allowing *just one dependency* would make for 
   more useful code, but... nah.  
* Not production pretty, necessarily. These classes are best regarded as erector
set pieces for rapid prototyping, not production deployment. This means you are free to:
   * use deprecated/risky things like Serialization
   * forgoing getters and setters in favor of direct field access
   * use Unsafe, etc. 
   * do evil reflection tricks
   * ignore casting warnings and such. (Linting not a priority)
   * don't feel compelled to do argument/bounds checking if there is no room 
* Inner classes/interfaces are fine, often necessary.
* It's probably best to lean on internal Java things, like thread pools and such.
   * really, there is a some fertile ground in some of the older, less popular
   Java library areas, I suspect. 
* One JUnit4 test file, which also functions as a good place to show how to use
the file; you can have test scoped dependencies.
* For the moment, language level of Java 8, though I am wondering about Java 11. 
But still, lots of folks are working in 8. Something to revisit.  

The priority is on working, reliable code, suitable for prototyping something larger.
Performance is not a top priority.  

So, the way I started this was with the ChiseledMap; I needed quick and dirty 
peristence, and some recent annoyances with third-party deps made me decide to 
see how fast I could build a thing. It was a short hop from "how fast" to "how small";
In the old days, when I started coding, small, tight code was *everything*.

"How small" somehow morphed into "let's see if I can do it in one file", and 
then -- I ended up with ChiseledMap, a useful single file persistent map. 
What I found was that it was wonderfully concentrating and freeing to 
restrict myself to no outside libraries, and only one file. 
It was also nice to ignore the "production quality" ogre a bit, since we 
deal with that beast at work all the time. Being free to 
use things like serialization, and not totally worry about perf can be very nice.

It's also easier to be restricted this way than you might think. The Java standard 
library is really rich, with a ton of adequate things built in. You'll be surprised
what it is like to code something this way; I would not have
bet that I could do a Single Decree Paxos impl in under 300 SLOCS, for example,
but the bounded nature of the language helped me stay focused. 

As a friend of mine, an awesome dev, would say: "Go code in a cave".

# Usage
Well, eventually there will be a maven jar one supposes, but really, just 
download the file you need.

Enjoy.
 
# Contributing

Feel free! Push a PR, we'll talk. Try and keep the guidelines in mind, obvs.   

#Ideas
Well, sure. Can someone write a parse engine that uses lambdas as actions in 1 file?
A really tight REST framework that can put up a REST endpoint fast? Etc, etc.

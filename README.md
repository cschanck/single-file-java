# single-file-java

What can you do in one file, no dependencies?

Modern Java is often a joy because of the extensive ecosystem. Lots of the time,
you can easily rely on lots of third-party libraries to help build your 
masterpiece. 

This is one of the great strengths of Java, no question!

However, it can be instructive to look backwards, back to a time before build systems
with automatic dependencies, back before frameworks upon frameworks, back when
if you wanted something to work -- you built it yourself. From scratch. 
Software minimalism.  

## Ground Rules
* 1000 lines in a file, no more than 120 chars on a line, commented enough 
to be useful
* 1 piece of functionality; i.e., collections of random misc utils don't count.
* **No dependencies** beyond test code deps for your single test file.  
* Not production pretty, necessarily. These classes are best regarded as erector
set pieces for rapid prototyping, not production deployment. This means you are free to:
   * use deprecated/risky things like Serialization
   * use Unsafe etc. 
   * do evil reflection tricks
   * ignore casting warnings and such. (Linting not a priority)
* Inner classes/interfaces are fine.
* It's probably best to lean on internal Java things, like thread pools and such.
* 1 JUnit 4 test file, which also functions as a good place to show how to use
the file 

The priority is on working, reliable code, suitable for prototyping something larger.
Performance is not a top priority.  

Generally, it can feel restrictive to code inside the single file approach, under 1000
lines. But the Java standard library is really rich, so it is more doable than
you think. Andit can make you concentrate your thinking in wonderful ways. I would not have
bet that I could do a Single Decree Paxos impl in under 300 SLOCS, for example.

# Usage
Well, eventually there will be a maven jar, but really, just download the file you need. 
Enjoy.
 
# Contributing

Feel free! Push a PR, we'll talk. Try and keep the guidelines in mind, obvs.   


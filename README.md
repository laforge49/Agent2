# agent2
2-way messaging for Clojure Agents.

When you send to an Agent there is no indication that the function
sent was executed or was dropped when the agent was restarted beyond
your own application code. This is typical of asynchronous programming,
which gives no assurances about process completion.

Asynchronous programming is hard. Timeouts are often used, but this can
be tricky when a function is not idempotent. And when a system is under
heavy load, timeouts often expire and tend to increase the load on
a system.

But when 2-way messages are supported, you can develop systems with a
guarantee that for every request there will be a response, if only
an error response. Such systems are still fully asynchronous, but a 
lot more fun to work with.

Remember that [Clojure agents](http://clojure.org/agents) are not intended for use directly across 
multiple JVM's. And this simplifies things enormously.

## Status

This project is a rewrite of the Java project, 
[JActor2](https://github.com/laforge49/JActor2), 
which makes it
easy to plan the implementation:

  - Two way messaging is complete.
  - Exception handling is complete.
  - Constrained time --todo.
  - Constrained resources --todo.
  - Single response assurance is complete.
  - Response assurance is complete.

## Architecture

The Clojure agent is used without change. A dynamic var, *context-atom*,
is used to provide a context map for the current request. These
context maps are organized as stacks and are used for returning responses
to a requesting agent within the context that made the request.

## Documentation

  - [Uberdoc](http://www.agilewiki.org/projects/agent2/uberdoc.html)
  - [API](http://www.agilewiki.org/projects/agent2/doc/index.html)
  
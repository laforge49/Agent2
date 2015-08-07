# agent2
2-way non-blocking messaging & weak real-time for [Clojure Agents](http://clojure.org/agents).

References in Clojure are generally to immutable values. Clojure
supports 4 types of references which differ in the mechanisms used
to make changes: Vars, Refs, Agents and Atoms. And in the case of 
Agents, the changes are made asynchronously. Updates to the value
of an agent are made by using 
[send](http://clojure.github.io/clojure/clojure.core-api.html#clojure.core/send) 
to pass a function to an agent.

Agents are modeled after Actors and the functions sent to an agent are 
evaluated one at a time. Polymorphism is a key feature, allowing
the same message to be processed appropriately depending on the value
of the agent. This is easily in Clojure, by always using the value of
an agent as the first argument given to any function sent to that agent
and by using a record as the value of the agent.

But unlike actors, agents always process messages in the order received.
This leads to 
"[Death by Accidental Complexity](http://www.infoq.com/presentations/Death-by-Accidental-Complexity)."
Actors solve this by processing messages based on actor state.
But this is not a solution without significant cost, as it introduces
coupling between actors and can result in frequent datalocks as a
project matures.

An alternative approach which allows messages to be processed in order was
pioneered by the 
[JActor2](https://github.com/laforge49/JActor2) 
project. Callbacks were used for handling non-blocking replies, with closures
managing a local state. But JActor2 was written in Java and everything
is easier when using Clojure.

## get-agent-value

We begin with a request function that replies with the value of the agent it was sent to:

    (defn get-agent-value
      [agent-value ctx-atom]
      (reply ctx-atom agent-value))

    (def agent42 (agent 42))
    (def r42 @(request-promise agent42 get-agent-value))
    (deftest test-get-agent-value
      (is (= r42 42)))



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
which makes it easy to plan the implementation:

  - Two way messaging is complete.
  - Exception handling is complete.
  - Constrained resources is complete.
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
  
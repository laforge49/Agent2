# agent2

2-way non-blocking messags for [Clojure Agents](http://clojure.org/agents).

[![Clojars Project](http://clojars.org/org.clojars.laforge49/agent2/latest-version.svg)](http://clojars.org/org.clojars.laforge49/agent2)

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
of the agent. This is easily achieved in Clojure by always using the value of
an agent as the first argument given to any function sent to that agent
and by using a record as the value of the agent.

But unlike actors, agents always process messages in the order received.
This leads to 
"[Death by Accidental Complexity](http://www.infoq.com/presentations/Death-by-Accidental-Complexity)."
Actors solve this by selectively processing messages based on actor state.
But this is not a solution without significant cost, as it introduces
coupling between actors and can result in frequent datalocks as a
project matures.

An alternative approach which allows messages to be processed in order was
pioneered by the 
[JActor2](https://github.com/laforge49/JActor2) 
project. Callbacks were used for handling non-blocking replies, with closures
managing a local state. But JActor2 was written in Java and everything
is easier when using Clojure.

(Unlike actors, Clojure agents do not scale across multiple JVMs. 
The JActor2 and agent2 projects also do not scale across multiple JVMs.)

## replies

We begin with a request function that replies with the value of the agent it was sent to:

```clojure
(ns agent2.examples
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn get-agent-value
  [agent-value ctx-atom]
  (reply ctx-atom agent-value))

(def agent42 (agent 42))
(def r42 (request-call agent42 get-agent-value))
(deftest test-get-agent-value
  (is (= r42 42)))
```

The first argument passed to the get-agent-value function is the value of the agent.
But for replies to work, we introduce a context, ctx-atom, which is passed as the
second argument.

To test this we use the request-call function, which passes get-agent-value to 
agent42 and then blocks until a result can be returned. 

## replies without blocking

Blocking to receive a reply is not something you want to do from
within an agent, as this would tie up a thread in the agent threadpool.
So lets look at an example where one agent sends a request to another:

```clojure
(defn get-indirect
  [_ ctx-atom agnt]
  (request ctx-atom agnt
           get-agent-value ()
           (fn [result] (reply ctx-atom result))))

(def agent99 (agent nil))
(def r99 @(request-call agent99 get-indirect agent42))
(deftest test-get-indirect
  (is (= r99 42)))
```

The get-indirect function uses request to send the get-agent-value
to another agent and then return the result. The request function
takes 5 arguments:

  1. The current context.
  1. The target agent.
  1. The function to be sent.
  1. A list of arguments for the function being sent. And
  1. The function to be sent back with the result.
  
The get-indirect function does not wait for a response. Rather,
it defines a callback which is evaluated when a response is 
received. In addition, processing a response is just like the processing
of any other message sent to an agent--only one message is processed at
a time.

## Documentation

  - [Uberdoc](http://www.agilewiki.org/projects/agent2/uberdoc.html)
  - [API](http://www.agilewiki.org/projects/agent2/doc/index.html)

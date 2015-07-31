(ns agent2.core)

;;# *context-atom*

(def ^{:dynamic true, :private true} *context-atom*
  "Holds the context of an operation performed on an agent."
  nil)

(defn- create-context-atom
  "Create an atom with the context map for operating on an agent:

     agent        - The agent to be operated on.
     properties   - Properties assigned to the context,
                    defaults to {}.
     src-ctx-atom - The atom for the operational context
                    creating the new context, defaults
                    to *context-atom*.

Minimum initial properties:

     :agent        - The agent to be operated on.
     :src-ctx-atom - The atom of the creating context.

Additional properties:

     :reply             - The callback function for
                          processing a response.
     :exception-handler - A function for processing an
                          exception.
     :agent-value       - The value of the agent.

Returns the new context atom."

  ([agent] (create-context-atom agent {}))
  ([agent properties] (create-context-atom agent
                                           properties
                                           *context-atom*))
  ([agent properties src-ctx-atom] (atom (conj properties [:agent agent]
                                               [:src-ctx-atom src-ctx-atom]))))

(defn context-get
  "Returns the value associated with a given key in the current context:

     key - The key into the context map."

  [key]
  (key @*context-atom*))

(defn context-assoc!
  "Associates a new value with a given key in the current context:

     key   - The key into the context map.
     value - The new value to be associated with the key."

  [key value]
  (swap! *context-atom* (fn [m] (assoc m key value))))

(declare exception-reply)

(defn- invoke-exception-handler
  "Invokes the exception handler, if any, to handle the exception. If
  there is no exception handler, or if the exception handler itself
  throws an exception, pass the original exception to the source:

     exception   - The exception to be passed to the
                   exception handler."

  [exception]
  (let [exception-handler (context-get :exception-handler)]
    (if (exception-handler)
      (try
        (exception-handler exception)
        (catch Exception e (exception-reply exception)))
      (exception-reply exception))))

(defn- process-action
  "Process an action:

     agent-value    - The value of the agent.
     [ctx-atom op]  - The action to be processed,
                      comprised of an operational
                      context atom and an operation.

Any exceptions thrown while processing the action are either passed to
the local exception handler or, failing that, passed to the source if
there is one. Unhandled exceptions then are given to the source agent
which received a signal.

Returns a new agent value."

  [agent-value [ctx-atom op]]
  (binding [*context-atom* ctx-atom]
    (context-assoc! :agent-value agent-value)
    (try
      (apply (first op) (rest op))
      (catch Exception e (invoke-exception-handler e)))
    (context-get :agent-value)
    )
  )

(defn signal
  "A 1-way message to operate on an agent.

     ag   - The agent to be operated on.
     f    - The function to operate on the agent.
     args - Optional arguments to f.

The f function takes the target agent's value as its first argument and
args as the remaining arguments. Its return value is ignored. This
function should use the set-agent-value function to update the state of
the agent.

Signals are unbuffered and are immediately passed to the target agent
via the send function. The signal function can be invoked from anywhere
as it does not itself use an operating context and should be used in
place of send because of the added support for request/reply."

  [ag f & args]
  (send ag process-action [(create-context-atom ag) (cons f args)]))

(defn request
  "A 2-way message exchange to operate on an agent and get a reply
  without blocking:

     ag   - The agent to be operated on.
     f    - The function which operates on the agent.
     args - Arguments to be passed to f. May be ().
     fr   - The callback function which processes the
             response.

The f function takes the target agent's value as its first argument and
args as the remaining arguments. Its return value is ignored. This
function should use the set-agent-value function to update the state of
the agent. A response is returned by calling the reply function.

The fr function takes two arguments, the value of the local agent and
the response returned by the reply function. This function is called
within the threadding context of the agent which invoked request. But
processing is asynchronous--there is no thread blocking. Rather, the
processing of requests and responses are interleaved. Isolation then
is an issue that must be managed by the application.

The request and reply functions can only be used when processing a
signal, request or response. Only signals then can be used elsewhere."

  [ag f args fr]
  (send ag process-action [(create-context-atom ag {:reply fr}) (cons f args)]))

(defn reply
  "Reply to a request via a buffered message:

     v - The response.

No response is sent if the operating context is for a signal rather
than for a request."

  [v]
  (let [src-ctx-atom (context-get :src-ctx-atom)]
    (if src-ctx-atom
      (let [fr (context-get :reply)
            src-agent (:agent @src-ctx-atom)]
        (send src-agent process-action [src-ctx-atom (list fr v)])))))

(defn- exception-processor
  "Processes an exception response by simply rethrowing the exception:

     agent-value - The value of the local context.
     exception   - The exception thrown while
                   processing a request."

  [exception]
  (throw exception))

(defn exception-reply
  "Pass an exception to the source which invoked the request, if any:

     exception - The exception.

No response is sent if the current operating context is for a signal
rather than for a request. Rather, the exception is simply thrown."

  [exception]
  (let [src-ctx-atom (context-get :src-ctx-atom)]
    (if src-ctx-atom
      (let [src-agent (:agent @src-ctx-atom)]
        (send src-agent [src-ctx-atom (list exception-processor exception)]))
      (throw exception))))

(defn- forward-request
  "Receives a request and returns the response via a future:

     p               - The future to hold the response.
     ag              - The target agent.
     f               - Function being sent.
     args            - Arg list of the function being sent."

  [p ag f args]
  (context-assoc! :exception-handler
    (fn [e]
      (deliver p e)))
  (request ag f args
           (fn [v]
             (deliver p v)
             ))
  )

(defn agent-future
  "Sends a function to an agent and returns a promise for the result:

     ag   - The target agent.
     f    - The function passed to the agent.
     args - Arguments passed to the function."

  [ag f & args]
  (let [p (promise)]
    (signal (agent "transporter") forward-request p ag f args)
    p
    ))

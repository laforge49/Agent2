(ns agent2.core)

;;# *context-atom*

(def ^{:dynamic true, :private true} *context-atom*
  "Holds the context of an operation performed on an agent."
  nil)

;;# create-context-atom

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

     :reply                - The callback function for
                             processing a response.
     :exception-handler    - A function for processing an
                             exception.
     :agent-value          - The value of the agent.
     :complete             - True once a response or an
                             exception has been sent.
     :outstanding-requests - Number of pending responses.
     :assure-response      - Requires either completion or
                             outstanding responses.

Returns the new context atom."

  ([agent]
   (create-context-atom agent {}))
  ([agent properties]
   (create-context-atom agent
                        properties
                        *context-atom*))
  ([agent properties src-ctx-atom]
   (atom (conj properties [:agent agent]
               [:src-ctx-atom src-ctx-atom]
               [:outstanding-requests 0]))))

;;# context-get-context-atom

(defn- context-get
  "Returns the value associated with a given key in the current context:

     key - The key into the context map."

  [key]
  (key @*context-atom*))

;;# context-assoc!

(defn- context-assoc!
  "Associates a new value with a given key in the current context:

     key   - The key into the context map.
     value - The new value to be associated with the key."

  [key value]
  (swap! *context-atom* (fn [m] (assoc m key value))))

;;# complete?

(defn- complete?
  "Returns true once a response or an exception has been sent."
  []
  (context-get :complete))

;;# ensure-response?

(defn- ensure-response?
  "Returns true if a response must be ensured."
  []
  (context-get :ensure-response))

;;# outstanding-requests?

(defn- outstanding-requests?
  "Returns true if there are pending requests."
  []
  (< 0 (context-get :outstanding-requests)))

;;# set-agent-value

(defn set-agent-value

  "Change the value of the agent:

     agent-value - The new agent value."

  [agent-value]
  (context-assoc! :agent-value agent-value))

;;# set-exception-handler

(defn set-exception-handler

  "Assign an exception handler to the context map of the agent:

     exception-handler - The new exception handler function.

  The exception handler function takes two arguments:

     agent-value - The value of the agent.
     exception   - The exception thrown."

  [exception-handler]
  (context-assoc! :exception-handler exception-handler))

;;# inc-outstanding

(defn- inc-outstanding
  "Add to the number of outstanding requests:

     increment - The amount to add."

  [increment]
  (context-assoc! :outstanding-requests
                  (+ increment (context-get :outstanding-requests))))

(declare exception-reply)

;;# invoke-exception-handler

(defn- invoke-exception-handler
  "Invokes the exception handler, if any, to handle the exception. If
  there is no exception handler, or if the exception handler itself
  throws an exception, pass the original exception to the source:

     ag-value  - The value of the agent.
     exception - The exception to be passed to the
                 exception handler."

  [ag-value exception]
  (let [exception-handler (context-get :exception-handler)]
    (if exception-handler
      (try
        (exception-handler ag-value exception)
        (catch Exception e (exception-reply exception)))
      (exception-reply exception))))

;;# process-action

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
    (if (complete?) (throw (Exception. "already closed.")))
    (context-assoc! :agent-value agent-value)
    (try
      (apply (first op) agent-value (rest op))
      (let [good (or (not (ensure-response?))
                    (complete?)
                    (outstanding-requests?))]
      (if-not good (throw (Exception. "Missing response"))))
      (catch Exception e
        (invoke-exception-handler agent-value e)))
    (context-get :agent-value)
    )
  )

;;# process-response

(defn- process-response
  [agent-value action]
  (inc-outstanding -1)
  (process-action agent-value action)
  )

;;# signal

(defn signal
  "A 1-way message to operate on an agent.

     ag   - The agent to be operated on.
     f    - The function to operate on the agent.
     args - Optional arguments to f.

The f function takes the target agent's value as its first argument and
args as the remaining arguments. Its return value is ignored. This
function should use the set-agent-value function to update the state of
the agent.

Signals are passed to the target agent
via the send function.

The request and reply functions can only be used when processing a
signal, request, response or exception. Signals and promise-request can be used
anywhere."

  [ag f & args]
  (send ag process-action [(create-context-atom ag) (cons f args)]))

;;# request

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
within the threading context of the agent which invoked the request. But
processing is asynchronous--there is no thread blocking. Rather, the
processing of requests and responses are interleaved. Isolation then
is an issue that must be managed by the application.

The request and reply functions can only be used when processing a
signal, request, response or exception. Signals and promise-request can be used
anywhere."

  [ag f args fr]
  (if (complete?) (throw (Exception. "already closed.")))
  (inc-outstanding 1)
  (send ag process-action [(create-context-atom ag {:reply fr
                                                    :ensure-response true})
                           (cons f args)]))

;;# reply

(defn reply
  "Reply to a request via a buffered message:

     v - The response.

No response is sent if the operating context is for a signal rather
than for a request.

Once reply is called, requests can not be sent, nor will
responses be processed.

The request and reply functions can only be used when processing a
signal, request, response or exception. Signals and promise-request
can be used anywhere."

  [v]
  (if-not (complete?)
    (let [src-ctx-atom (context-get :src-ctx-atom)]
      (context-assoc! :complete true)
      (if src-ctx-atom
        (let [fr (context-get :reply)
              src-agent (:agent @src-ctx-atom)]
          (send src-agent process-response [src-ctx-atom (list fr v)]))))))

;;# exception-processor

(defn- exception-processor
  "Processes an exception response by simply rethrowing the exception:

     agent-value - The value of the local context.
     exception   - The exception thrown while
                   processing a request."

  [agent-value exception]
  (throw exception))

;;# exception-reply

(defn- exception-reply
  "Pass an exception to the source which invoked the request, if any:

     exception - The exception.

Once reply is called, signals and requests can not be sent, nor will
responses be processed.

No response is sent if the current operating context is for a signal
rather than for a request. Rather, the exception is simply thrown."

  [exception]
  (if-not (complete?)
    (let [src-ctx-atom (context-get :src-ctx-atom)]
      (context-assoc! :complete true)
      (if src-ctx-atom
        (let [src-agent (:agent @src-ctx-atom)]
          (send src-agent process-response
                [src-ctx-atom (list exception-processor exception)]))
        (throw exception)))))

;;# forward-request

(defn- forward-request
  "Receives a request and returns the response via a promise:

     agent-value     - The value of the agent.
     p               - The promise to hold the response.
     ag              - The target agent.
     f               - Function being sent.
     args            - Arg list of the function being sent.

The function f is invoked with the target agent's value
pre-pended to its list of args."

  [agent-value p ag f args]
  (context-assoc! :exception-handler
                  (fn [e]
                    (deliver p e)))
  (request ag f args
           (fn [agent-value v]
             (deliver p v)
             ))
  )

;;# agent-future

(defn agent-promise
  "Sends a function to an agent and returns a promise for the result:

     ag   - The target agent.
     f    - The function passed to the agent.
     args - Arguments passed to the function.

The function f is invoked with the target agent's value
pre-pended to its list of args.

The request and reply functions can only be used when processing a
signal, request, response or exception. Signals and promise-request can be used
anywhere.

Just remember when using agent-promise while processing a signal, request,
response or exception that there are only a few threads in the default
threadpool and blocking a thread to dereference a promise is generally
not a good idea."

  [ag f & args]
  (let [p (promise)]
    (signal (agent "transporter") forward-request p ag f args)
    p
    ))

(ns agent2.core)
(ns agent2.core)

;;# *context-atom*

(def ^{:dynamic true, :private true} *context-atom*
  "Holds the operational context of the agent being operated on.")

(def ^{:dynamic true, :private true} *agent-value*
  "Current value of the agent being operated on.")

(defn create-context-atom
  "Create an atom with the operational context for operating on an agent:

     agent        - The agent to be operated on.
     properties   - Properties assigned to the context,
                    defaults to {}.
     src-ctx-atom - The atom for the operational context
                    creating the new context, defaults
                    to *context-atom*.

Minimum initial properties:

     :agent        - The agent to be operated on.
     :src-ctx-atom - The atom for the creating context.
     :unsent       - Buffered requests/responses which
                     have not yet been sent.

Additional properties:

     :reply             - The callback function for
                          processing a response.
     :exception-handler - A function for processing an
                          exception.

Returns the new context atom."

  ([agent] (create-context-atom agent {}))
  ([agent properties] (create-context-atom agent properties *context-atom*))
  ([agent properties src-ctx-atom] (atom (conj properties [:agent agent]
                                               [:src-ctx-atom src-ctx-atom]
                                               [:unsent []]))))

(defn get-agent
  "Returns the agent being operated on."
  []
  (:agent @*context-atom*))

(defn get-agent-value
  "Returns the value of the agent being operated on.
  This may be the current value of the agent, or the last value bound with
  set-agent-value!."
  []
  *agent-value*)

(defn set-agent-value!
  "Bind a new value to be given to the agent once the current operation is complete."
  [value]
  (def ^:dynamic *agent-value* value)
  )

(defn get-exception-handler
  "Returns the exception handler bound to the operating context, or nil."
  []
  (:excption-handler @*context-atom*))

(defn set-exception-handler!
  "Bind a new exception handler function to the operating context:

     exception-handler - The function which will handle
                         any excptions.

  The exception handler function must have two arguments, the agent value and the exception
  to be handled."

  [exception-handler]
  (reset! *context-atom* (assoc @*context-atom* :exception-handler exception-handler))
  )

(declare process-actions exception-reply)

(defn- invoke-exception-handler
  "Invokes the exception handler, if any, to handle the exception. If there is no exception
  handler, or if the exception handler itself throws an exception, pass the original exception
  to the source:

     agent-value - The value of the agent.
     exception   - The exception to be passed to the
                   exception handler."

  [agent-value exception]
  (if (get-exception-handler)
    (try
      ((get-exception-handler) agent-value exception)
      (catch Exception e (exception-reply exception)))
      (exception-reply exception)))

  (defn- process-action
    "Process a single action:

       grouped-unsent - The actions not yet sent to
                        other agents.
       [ctx-atom op]  - The action to be processed,
                        comprised of an operational
                        context atom and an operation.

  Any exceptions thrown while processing the action are either passed to the local exception
  handler or, failing that, passed to the source if there is one. Unhandled exceptions then
  are given to the source agent which received a signal.

  Returns grouped-unsent with any additional unsent actions grouped by destination agent."

    [grouped-unsent [ctx-atom op]]
    (binding [*context-atom* ctx-atom]
    (try
      (apply (first op) (get-agent-value) (rest op))
      (catch Exception e (invoke-exception-handler (get-agent-value) e)))
    (let [unsent (:unsent @ctx-atom)
          grouped-unsent (reduce
                           #(assoc-in %1 [(first %2)] (second %2))
                           grouped-unsent
                           unsent)]                         ; merge the new requests/responses into grouped-unsent.
      (reset! *context-atom* (assoc-in @ctx-atom [:unsent] [])) ; clear :unsent in the context atom.
      grouped-unsent
      )))

  (defn- send-actions
    "Send all the buffered actions for a given agnet."
    [[agent actions]]
    (send agent process-actions actions))

  (defn- process-actions
    "This function is passed to an agent and subsequently invoked by same:

       old-agent-value - The current value of the agent,
                         provided by the agent itself.
       actions         - The actions passed with this
                         function to an agent for
                         operating on that agent.

  After all the actions have been processed the buffered actions are sent to their
  destination agents in groups.

  Returns an updated value for the agent."

    [old-agent-value actions]
    (def ^:dynamic *agent-value* old-agent-value)
    (dorun (map send-actions
                (reduce process-action {} actions)))
    *agent-value*)

  (defn signal
    "An unbuffered, 1-way message to operate on an agent.

       agent - The agent to be operated on.
       f     - The function to operate on the agent.
       args  - Optional arguments to f.

  The f function takes the target agent's value as its first argument and args as
  the remaining arguments. Its return value is ignored. This function should use the set-agent-value
  function to update the state of the agent.

  Signals are unbuffered and are immediately passed to the target agent via the send function.
  The signal function can be invoked from anywhere as it does not itself use an operating context
  and should be used in place of send because of the added support for request/reply."

    [agent f & args]
    (send agent process-actions (list [(create-context-atom agent) (cons f args)])))

  (defn request
    "A buffered 2-way message exchange to operate on an agent and get a reply without blocking:

       agent - The agent to be operated on.
       f     - The function which operates on the agent.
       args  - Arguments to be passed to f. May be ().
       fr    - The callback function which processes the
               response.

  The f function takes the target agent's value as its first argument and args as
  the remaining arguments. Its return value is ignored. This function should use the set-agent-value
  function to update the state of the agent. A response is returned by calling the reply function.

  The fr function takes two arguments, the value of the local agent andthe response returned by the reply function.
  This function is called within the threadding context of the agent which invoked request.
  But processing is asynchronous--there is no thread blocking. Rather, the processing of requests
  and responses are interleaved. Isolation then is an issue that must be managed by the application.

  The request and reply functions can only be used when processing a signal, request or response. Only signals
  then can be used elsewhere."

    [agent f args fr]
    (let [context @*context-atom*
          ctx-atom (create-context-atom agent {:reply fr})
          unsent (:unsent context)
          msg [agent (list [ctx-atom (cons f args)])]
          unsent (conj unsent msg)]
      (reset! *context-atom* (assoc-in context [:unsent] unsent))))

  (defn reply
    "Reply to a request via a buffered message:

       v - The response.

  No response is sent if the operating context is for a signal rather than for a request."

    [v]
    (let [context @*context-atom*
          fr (:reply context)]
      (if fr
        (let [src-ctx-atom (:src-ctx-atom context)
              src-agent (:agent @src-ctx-atom)
              unsent (:unsent context)
              msg [src-agent (list [src-ctx-atom (list fr v)])]
              unsent (conj unsent msg)]
          (reset! *context-atom* (assoc-in context [:unsent] unsent))))))

  (defn- exception-processor
    "Processes an exception response by simply rethrowing the exception:

       agent-value - The value of the local context.
       exception   - The exception thrown while
                     processing a request."

    [agent-value exception]
    (throw exception))

  (defn exception-reply
    "Pass an exception to the source which invoked the request, if any:

       exception - The exception.

  No response is sent if the current operating context is for a signal rather than for a request.
  Rather, the exception is simply thrown."

    [exception]
    (let [context @*context-atom*
          src-ctx-atom (:src-ctx-atom context)]
      (if src-ctx-atom
        (let [src-agent (:agent @src-ctx-atom)
              unsent (:unsent context)
              msg [src-agent (list [src-ctx-atom (list exception-processor exception)])]
              unsent (conj unsent msg)]
          (reset! *context-atom* (assoc context :unsent unsent)))
        (throw exception))))

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

Returns the new context atom."

  ([agent] (create-context-atom agent {}))
  ([agent properties] (create-context-atom agent properties *context-atom*))
  ([agent properties src-ctx-atom] (atom (conj properties [:agent agent]
                                               [:src-ctx-atom src-ctx-atom]
                                               [:unsent []]))))

(defn- get-agent
  "Returns the agent being operated on from the *context-atom*."
  [] (:agent @*context-atom*))

(defn get-agent-value
  "Returns the value of the agent being operated on.
  This may be the current value of the agent, or the last value used with
  set-agent-value."
  []
  *agent-value*)

(defn set-agent-value
  "Bind a new value to be given to the agent once the current operation is complete."
  [value]
  (def ^:dynamic *agent-value* value)
  )

(declare process-actions)

(defn- process-action
  "Process a single action:

     grouped-unsent - The actions not yet sent to other
                      agents.
     [ctx-atom op]  - The action to be processed,
                      comprised of an operational
                      context atom and an operation.

Returns grouped-unsent with any additional unsent actions grouped by destination agent."

  [grouped-unsent [ctx-atom op]]
  (def ^:dynamic *context-atom* ctx-atom)
  (apply (first op) (get-agent-value) (rest op))
  (let [unsent (:unsent @ctx-atom)
        grouped-unsent (reduce
                         #(assoc-in %1 [(first %2)] (second %2))
                         grouped-unsent
                         unsent)]                           ; merge the new requests/responses into grouped-unsent.
    (reset! *context-atom* (assoc-in @ctx-atom [:unsent] [])) ; clear :unsent in the context atom.
    grouped-unsent
    ))

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
The signal function can be invoked from anywhere as it does not itself use an operating context.
The signal method should be used in place of send because of the added support for request/reply."

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

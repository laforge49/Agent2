(ns agent2.core)

(def ^:dynamic context2 nil)

(defn agent2
  "Create an agent2"
  ([] (agent2 nil))
  ([state] (apply agent {:gate  (atom :idle)
                         :state (atom state)} nil))
  )

(defn create-context
  "Create an operational context for operating on an actor"
  ([a] (create-context a {}))
  ([a d] (create-context a d context2))
  ([a d ctx] (atom (conj d [:agent a]
                         [:src-ctx ctx]
                         [:unsent []]))))

(defn- get-agent [] (:agent @context2))

(defn get-state
  "Access the state within the context of an agent"
  []
  (:state @(get-agent)))

(defn reset-state
  "Update the state within the context of an agent"
  [v]
  (reset! (get-state) v))

(declare process-actions)

(defn- process-action
  [grouped-unsent [ctx op]]
  (def ^:dynamic context2 ctx)
  (eval op)
  (let [unsent (:unsent @ctx)
        grouped-unsent
        (reduce
          #(assoc-in %1 [(first %2)] (second %2))
          grouped-unsent
          unsent)]
    (reset! context2 (assoc-in @ctx [:unsent] []))
    grouped-unsent
  ))

(defn- send-actions
  [[a2 actions]]
  (send a2 process-actions actions))

(defn- process-actions
  [old-state actions]
  (let [gate (:gate old-state)]
    (while (not (compare-and-set! gate :idle :busy)))
    (dorun (map send-actions
                (reduce process-action {} actions)))
    (reset! gate :idle)
    old-state))

(defn signal
  "An unbuffered 1-way message to operate on an actor.
  a2 - the agent to be operated on.
  f - the function to operate on a2.

  The f function takes no arguments and its return value is ignored.
  This function should use the get-state and set-state functions to
  access the state of agent a2."
  [a2 f]
  (send a2 process-actions (list [(create-context a2) (list f)])))

(defn request
  "A buffered 2-way message exchange to operate on an agent and get a reply.
  a2 - the agent to be operated on.
  f - the function which operates on a2.
  fr - the function which processes the response.

  The f function takes no arguments and its return value is ignored.
  This function should use the get-state and set-state functions to
  access the state of agent a2. A reply is sent by calling the return2 function.

  The fr function also takes one argument, the value sent back by the return2 function.
  this function is called within the threadding context of the actor which called send2.
  But processing is asynchronous--there is no thread blocking."
  [a2 f fr]
  (let [context @context2
        ctx (create-context a2 {:reply fr})
        unsent (:unsent context)
        msg [a2 (list [ctx (list f)])]
        unsent (conj unsent msg)]
    (reset! context2 (assoc-in context [:unsent] unsent))))

(defn reply
  "Reply to a request via a buffered message."
  [v]
  (let [r (:reply @context2)]
    (if r
      (let [context @context2
            ctx (:src-ctx context)
            a2 (:agent @ctx)
            unsent (:unsent context)
            msg [a2 (list [ctx (list r v)])]
            unsent (conj unsent msg)]
        (reset! context2 (assoc-in context [:unsent] unsent))))))

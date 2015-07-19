(ns agent2.core)

(def ^:dynamic context2 nil)

(defn agent2
  "Create an agent2"
  ([] (agent2 nil))
  ([state] (apply agent {:gate  (atom :idle)
                         :state (atom state)} nil))
  )

(defn get-state
  "Access the state within the context of an agent"
  []
  @(:state @(:target @context2)))

(defn set-state
  "Update the state within the context of an agent"
  [v]
  (reset! (:state @(:target @context2)) v))

(defn ctx2
  "Create an operational context for operating on an actor"
  ([a] (ctx2 a {}))
  ([a d] (ctx2 a d context2))
  ([a d ctx] (atom (conj d [:target a]
                         [:src-ctx ctx]
                         [:unsent []]))))

(defn- pass2all
  "Pass all unsent requests and responses"
  []
  (let [ctx @context2]
    (doall (map #(apply (first %) (rest %)) (:unsent ctx)))
    (reset! context2 (assoc-in ctx [:unsent] []))
    ))

(defn- process2
  "Process an incoming operation"
  [old-state c2 action]
  (let [gate (:gate old-state)]
    (def ^:dynamic context2 c2)
    (while (not (compare-and-set! gate :idle :busy)))
    (eval action)
    (reset! gate :idle)
    (pass2all)
    )
  old-state)

(defn signal
  "An unbuffered 1-way message to operate on an actor.
  a2 - the agent to be operated on.
  f - the function to operate on a2.

  The f function takes no arguments and its return value is ignored.
  This function should use the get-state and set-state functions to
  access the state of agent a2."
  [a2 f]
  (send a2 process2 (ctx2 a2) (list f)))

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
        unsent (:unsent context)
        msg (list send a2
                  process2
                  (ctx2 a2 {:return fr})
                  (list f))
        unsent (conj unsent msg)]
    (reset! context2 (assoc-in context [:unsent] unsent))))

  (defn reply
    "Reply to a request via a buffered message."
    [v]
    (let [r (:return @context2)]
      (if r
        (let [c @context2
              ctx (:src-ctx c)
              a (:target @ctx)]
          (send a process2 ctx (list r v))))))

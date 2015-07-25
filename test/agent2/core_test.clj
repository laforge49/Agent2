(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state [agent-value]
  (set-agent-value! (+ 1 agent-value))
  )

(defn return-state
  [agent-value]
  (reply agent-value))

(def a (agent 2))
(signal (agent nil)
        (fn [agent-value]
          (signal a inc-state)
          (request a return-state ()
                   (fn [agent-value v]
                     (println "response:" v)
                     (shutdown-agents)
                     ))
          )
        )

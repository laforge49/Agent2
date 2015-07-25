(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state []
  (let [old (get-agent-value)]
    (set-agent-value (+ 1 old)))
  )

(defn return-state
  []
  (reply (get-agent-value)))

(def a (agent 2))
(signal (agent nil)
         (fn []
           (signal a inc-state)
           (request a return-state
                  (fn [v] (println "response:" v)
                    (shutdown-agents)
                    ))
           )
         )

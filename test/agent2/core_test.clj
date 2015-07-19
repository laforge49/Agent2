(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state []
  (let [old @(get-state)]
    (reset-state (+ 1 old)))
  )

(defn return-state
  []
  (reply @(get-state)))

(def a (agent2 2))
(signal (agent2)
         (fn []
           (signal a inc-state)
           (request a return-state
                  (fn [v] (println "response:" v)
                    (shutdown-agents)
                    ))
           )
         )

(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state []
  (let [old (get-state)]
    (set-state (+ 1 old)))
  )

(defn return-state
  []
  (return2 (get-state)))

(def a (agent2 2))
(signal2 (agent2)
         (fn []
           (signal2 a inc-state)
           (send2 a return-state
                  (fn [v] (println "response:" v)
                    (shutdown-agents)
                    ))
           )
         )

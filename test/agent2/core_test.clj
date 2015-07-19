(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(def agent2-a (agent2 2))

(defn prn-state [] (prn (get-state)))
(defn inc-state []
  (let [old (get-state)]
    (setState (+ 1 old))))

(println agent2-a)
(signal2 agent2-a 'inc-state)
(prn agent2-a)
(prn agent2-a)

(defn return-state
  []
  (return2 (get-state)))

(defn got-response
  [v]
  (println "response:" v)
  (shutdown-agents))

(def agent2-b (agent2 666)) ; agent for receiving response
(set-context2 (ctx2 agent2-b {} nil)) ; context for sending from main thread and receiving response
(send2 agent2-a 'return-state 'got-response)
(pass2all)

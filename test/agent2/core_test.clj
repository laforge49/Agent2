(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state [agent-value]
  (set-agent-value! (+ 1 agent-value))
  )

(defn return-state
  [agent-value]
  (reply agent-value))

(comment
  (def a1 (agent 22))
  (signal a1 inc-state)
  (def r1 (agent-future a1 return-state))
  (println r1)
  )

(def a (agent 2))
(def p (promise))
(signal (agent nil)
        (fn [_]
          (signal a inc-state)
          (request a return-state ()
                   (fn [_ v]
                     (deliver p v)
                     ))
          )
        )
(def q (deref p 1200 nil))
;(println q)
(deftest basic
  (is (= 3 q)))

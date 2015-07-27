(ns agent2.exceptions
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn dbz [_]
  (/ 0 0))

(defn dbzr [_ _]
  (/ 0 0))

;(comment
  (def p1 (promise))
  (defn eh1 [a e]
    (deliver p1 true))
  (def a1 (agent true :error-handler eh1))
  (send a1 dbz)
  (def q1 (deref p1 200 false))
  (deftest agent-error-handler-1
    (is (= true q1)))
;  )

;(comment
  (def p2 (promise))
  (defn eh2 [a e]
    (deliver p2 true)
    )
  (def a2 (agent true :error-handler eh2))
  (signal a2 dbz)
  (def q2 (deref p2 200 false))
  (deftest signal-error-handler-2
    (is (= true q2)))
;  )

;(comment
(def p3 (promise))
(defn eh3 [a e]
  (deliver p3 "got error"))
(def a3 (agent true :error-handler eh3))
(def b3 (agent "Fred"))
(signal a3
        (fn [agent-value]
          (request b3
                   dbz ()
                   '(println 99))))
(def q3 (deref p3 200 "timeout"))
(deftest request-error-handler-3
  (is (= q3 "got error")))
;  )

;(comment
  (def p4 (promise))
  (defn eh4 [a e]
    (deliver p4 "got error"))
  (def a4 (agent true :error-handler eh4))
  (def b4 (agent "Fred"))
  (signal a4
          (fn [agent-value]
            (request b4
                     (fn [_] (reply 42)) ()
                     dbzr)))
  (def q4 (deref p4 200 nil))
  (deftest reply-error-handler-4
    (is (= q4 "got error")))
;  )

;(comment
  (def p5 (promise))
  (defn exh5 [_ _]
    (deliver p5 "got exception"))
  (def a5 (agent "Sam"))
  (signal a5 (fn [_]
               (set-exception-handler! exh5)
               (/ 0 0)))
  (def q5 (deref p5 1200 "timeout"))
  (deftest signal-exception-handler-5
    (is (= "got exception" q5)))
;  )

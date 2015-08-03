(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state [agent-value]
  (set-agent-value (+ 1 agent-value))
  )

(defn return-state
  [agent-value]
  (reply agent-value))

(defn eh [a e]
  (println "got error" e)
  (.printStackTrace e))

(def a22 (agent 22 :error-handler eh))
(signal a22 inc-state)
(def r22 @(agent-promise a22 return-state))
(deftest promise-request
  (is (= 23 r22)))

(def a33 (agent 33))
(defn ignore-result [_ _] (println "ignoring"))
(defn check33 [_]
  (request a33 return-state () ignore-result)
  (request a33 return-state () ignore-result)
  (request a33 return-state () ignore-result)
  (reply 999)
  )
(def a34 (agent 34))
(def r34 @(agent-promise a34 check33))
(println r34)

(defn dbz [_]
  (/ 0 0))

(defn dbzr [_ _]
  (/ 0 0))

(def p1 (promise))
(defn eh1 [a e]
  (deliver p1 true))
(def a1 (agent true :error-handler eh1))
(send a1 dbz)
(def q1 (deref p1 200 false))
(deftest agent-error-handler-1
  (is (= true q1)))

(def p2 (promise))
(defn eh2 [a e]
  (deliver p2 true)
  )
(def a2 (agent true :error-handler eh2))
(signal a2 dbz)
(def q2 (deref p2 200 false))
(deftest signal-error-handler-2
  (is (= true q2)))

(def p3 (promise))
(defn eh3 [a e]
  (deliver p3 "got error"))
(defn eh3b [a e]
  (.printStackTrace e))
(def a3 (agent true :error-handler eh3))
(def b3 (agent "Fred" :error-handler eh3b))
(signal a3
        (fn [agent-value]
          (request b3
                   dbz ()
                   '(println 99))))
(def q3 (deref p3 200 "timeout"))
(deftest request-error-handler-3
  (is (= q3 "got error")))

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

(defn eh5 [a e]
  (.printStackTrace e))
(def p5 (promise))
(defn exh5 [_ _]
  (deliver p5 "got exception"))
(def a5 (agent "Sam" :error-handler eh5))
(signal a5 (fn [_]
             (set-exception-handler exh5)
             (/ 0 0)))
(def q5 (deref p5 1200 "timeout"))
(deftest signal-exception-handler-5
  (is (= "got exception" q5)))

(def p6 (promise))
(defn eh6 [a e]
  (.printStackTrace e)
  (deliver p6 "got error"))
(defn exh6 [_ _]
  (deliver p6 "got exception"))
(def a6 (agent true :error-handler eh6))
(def b6 (agent "Fred"))
(signal a6
        (fn [agent-value]
          (set-exception-handler exh6)
          (request b6
                   dbz ()
                   '(println 99))))
(def q6 (deref p6 200 "timeout"))
(deftest request-error-handler-6
  (is (= q6 "got exception")))

(def p7 (promise))
(defn eh7 [a e]
  (deliver p7 "got error"))
(defn exh7 [_ _]
  (deliver p7 "got exception"))
(def a7 (agent true :error-handler eh7))
(def b7 (agent "Fred"))
(signal a7
        (fn [agent-value]
          (set-exception-handler exh7)
          (request b7
                   (fn [_] (reply 42)) ()
                   dbzr)))
(def q7 (deref p7 200 nil))
(deftest reply-error-handler-7
  (is (= q7 "got exception")))

(def a98 (agent 98))
(def r98 (.getMessage @(agent-promise a98 (fn [_] (set-exception-handler
                                                   (fn [_ e]
                                                     (reply e)))))))
(deftest missing-response
  (is (= r98 "Missing response")))
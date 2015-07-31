(ns agent2.core-test
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn inc-state []
  (context-assoc! :agent-value (+ 1 (context-get :agent-value)))
  )

(defn return-state
  []
  (reply (context-get :agent-value)))

(defn eh [a e]
  (println "got error" e))

(def a22 (agent 22 :error-handler eh))
(signal a22 inc-state)
(def r22 @(agent-future a22 return-state))
(deftest promise-request
  (is (= 23 r22)))
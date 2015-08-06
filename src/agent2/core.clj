(ns agent2.core)

;;# *context-atom*

(def ^{:dynamic true, :private true} *context-atom*
  "Holds the context of an operation performed on an agent."
  nil)

;;# create-context-atom

(defn- create-context-atom
  "Create an atom with the context map for operating on an agent:

     agent        - The agent to be operated on.
     properties   - Properties assigned to the context.
     src-ctx-atom - The atom for the operational context
                    creating the new context, defaults
                    to *context-atom*.

Minimum initial properties:

     :agent            - The agent to be operated on.
     :src-ctx-atom     - The atom of the creating context.
     :requests-counter - A count of the number of
                         requests that have been sent.
     :request-depth    - The max allowed depth of requests.
     :complete-atom    - True once a response or an
                         exception has been sent.

Additional properties:

     :reply                - The callback function for
                             processing a response.
     :promise              - A promise used to return a
                             response.
     :exception-handler    - A function for processing an
                             exception.
     :agent-value          - The value of the agent.
     :outstanding-requests - Number of pending responses.
     :assure-response      - Requires either completion or
                             outstanding responses.
                             Set when a request is sent.
     :max-requests         - Upper bound on the number of
                             requests that can be sent.
     :requests-counter     - A count of the number of
                             requests that have been sent.
Returns the new context atom."

  ([agent properties]
   (create-context-atom agent
                        properties
                        *context-atom*))
  ([agent properties src-ctx-atom]
   (atom (conj properties [:agent agent]
               [:src-ctx-atom src-ctx-atom]
               [:outstanding-requests 0]))))

;;# context-get

(defn- context-get
  "Returns the value associated with a given key in the current context:

     ctx-atom - Defaults to *context-atom*.
     key      - The key into the context map."

  ([key] (context-get *context-atom* key))
  ([ctx-atom key] (key @ctx-atom)))

;;# context-assoc!

(defn- context-assoc!
  "Associates a new value with a given key in the current context:

     key   - The key into the context map.
     value - The new value to be associated with the key."

  ([key value] (context-assoc! *context-atom* key value))
  ([ctx-atom key value] (swap! ctx-atom (fn [m] (assoc m key value)))))

;;# complete?

(defn- complete?

  "Returns true once a response or an exception has been sent."

  ([] (complete? *context-atom*))
  ([ctx-atom] @(context-get ctx-atom :complete-atom)))

;;# ensure-response?

(defn- ensure-response?
  "Returns true if a response must be ensured."
  ([] (ensure-response? *context-atom*))
  ([ctx-atom] (context-get ctx-atom :ensure-response)))

;;# outstanding-requests?

(defn- outstanding-requests?
  "Returns true if there are pending requests."
  ([] (outstanding-requests? *context-atom*))
  ([ctx-atom] (< 0 (context-get ctx-atom :outstanding-requests))))

;;# set-agent-value

(defn set-agent-value

  "Change the value of the agent:

     agent-value - The new agent value.

The set-agent-value function can only be used within the scope of a context map."

  ([agent-value] (set-agent-value *context-atom* agent-value))
  ([ctx-atom agent-value] (context-assoc! ctx-atom :agent-value agent-value)))

;;# set-max-requests

(defn set-max-requests

  "Set the maximum number of requests that can be sent:

     max-requests - The limit before an exception is thrown.

The set-max-response function can only be used within the scope of a context map."

  ([max-requests] (set-max-requests *context-atom* max-requests))
  ([ctx-atom max-requests] (context-assoc! ctx-atom :max-requests max-requests)))

;;# reduce-request-depth

(defn reduce-request-depth

  "Set request-depth to a smaller value:

     new-request-depth - The smaller value.

Default value is Integer/MAX_VALUE.

The reduce-request-depth function can only be used within the scope of a context map."

  ([new-request-depth] (reduce-request-depth *context-atom* new-request-depth))
  ([ctx-atom new-request-depth] (let [request-depth (context-get ctx-atom :request-depth)]
                                  (if (< new-request-depth request-depth)
                                    (context-assoc! ctx-atom :request-depth new-request-depth)))))

;;# set-exception-handler

(defn set-exception-handler

  "Assign an exception handler to the context map of the agent:

     exception-handler - The new exception handler function.

  The exception handler function takes one argument:

     exception   - The exception thrown.

The set-exception-handler function can only be used within the scope of a context map."

  ([exception-handler] (set-exception-handler *context-atom* exception-handler))
  ([ctx-atom exception-handler] (context-assoc! ctx-atom :exception-handler exception-handler)))

;;# context-inc

(defn- context-inc
  "Add to the selected value in the context map:

     key       - Selects the value.
     increment - The amount to add."

  ([key increment] (context-inc *context-atom* key increment))
  ([ctx-atom key increment] (context-assoc! ctx-atom key
                                   (+ increment (context-get ctx-atom key)))))

;;# get-context-atom

(defn clear-ensure-response

  "Clears the ensure-response flag.

  The get-context-atom function can only be used within the scope of a context map."

  [ctx-atom] (context-assoc! ctx-atom :ensure-response nil))

(declare exception-reply)

;;# invoke-exception-handler

(defn- invoke-exception-handler
  "Invokes the exception handler, if any, to handle the exception. If
  there is no exception handler, or if the exception handler itself
  throws an exception, pass the original exception to the source:

     exception - The exception to be passed to the
                 exception handler."

  ([exception] (invoke-exception-handler *context-atom* exception))
  ([ctx-atom exception]
  (let [exception-handler (context-get ctx-atom :exception-handler)]
    (if exception-handler
      (try
        (exception-handler exception)
        (catch Exception e (exception-reply ctx-atom exception)))
      (exception-reply ctx-atom exception)))))

;;# process-operation

(defn- process-operation
  "Process an action:

     f    - The function to be evaluated.
     args - Arguments to the function.

Any exceptions thrown while processing the action are either passed to
the local exception handler or, failing that, passed to the source if
there is one. Unhandled exceptions then are given to the source agent
which received a signal.

Returns a new agent value."

  ([f args] (process-operation *context-atom* f args))
  ([ctx-atom f args]
  (if (nil? (context-get ctx-atom :max-requests))
    (context-assoc! ctx-atom :max-requests Long/MAX_VALUE))
  (try
    (apply f args)
    (let [good (or (not (ensure-response? ctx-atom))
                   (complete? ctx-atom)
                   (outstanding-requests? ctx-atom))]
      (if-not good (throw (Exception. "Missing response"))))
    (catch Exception e
      (invoke-exception-handler ctx-atom e)))
  (context-get ctx-atom :agent-value)
  ))

;;# process-request

(defn- process-request

  "Process an action sent by a request or request-promise."

  ([agent-value [ctx-atom op]]
  (binding [*context-atom* ctx-atom]
    (context-assoc! ctx-atom :agent-value agent-value)
    (process-operation ctx-atom (first op) (cons agent-value (rest op)))
    )))

;;# process-response

(defn- process-response
  "Process a response sent by a reply or reply-exception."
  [agent-value [ctx-atom op]]
  (binding [*context-atom* ctx-atom]
    (context-assoc! ctx-atom :agent-value agent-value)
    (context-inc ctx-atom :outstanding-requests -1)
    (process-operation ctx-atom (first op) (rest op))
    ))

;;# signal

(defn signal
  "A 1-way message to operate on an agent.

     ag   - The agent to be operated on.
     f    - The function to operate on the agent.
     args - Optional arguments to f.

The f function takes the target agent's value as its first argument and
args as the remaining arguments. Its return value is ignored. This
function should use the set-agent-value function to update the state of
the agent.

Signals are passed to the target agent
via the send function.

The signal function can be used anywhere."

  ([ag f & args]
  (send ag process-request [(create-context-atom ag {:requests-counter 0
                                                     :request-depth    Integer/MAX_VALUE
                                                     :complete-atom    (atom nil)}
                                                 *context-atom*)
                            (cons f args)])))

;;# request

(defn request
  "A 2-way message exchange to operate on an agent and get a reply
  without blocking:

     ag   - The agent to be operated on.
     f    - The function which operates on the agent.
     args - Arguments to be passed to f. May be ().
     fr   - The callback function which processes the
             response.

The f function takes the target agent's value as its first argument and
args as the remaining arguments. Its return value is ignored. This
function should use the set-agent-value function to update the state of
the agent. A response is returned by calling the reply function.

The fr function takes one argument,
the response returned by the reply function. This function is called
within the threading context of the agent which invoked the request. But
processing is asynchronous--there is no thread blocking. Rather, the
processing of requests and responses are interleaved. Isolation then
is an issue that must be managed by the application.

The request function can only be used within the scope of a context map."

  ([ag f args fr] (request *context-atom* ag f args fr))
  ([ctx-atom ag f args fr]
  (if (complete? ctx-atom) (throw (Exception. "already closed.")))
  (context-inc ctx-atom :outstanding-requests 1)
  (context-inc ctx-atom :requests-counter 1)
  (when (> (context-get ctx-atom :requests-counter) (context-get ctx-atom :max-requests))
    (throw (Exception. "Exceeded max requests")))
  (let [request-depth (context-get ctx-atom :request-depth)]
    (when (= 0 request-depth)
      (throw (Exception. "Exceeded request depth"))
      )
    (send ag process-request [(create-context-atom ag {:reply            fr
                                                       :ensure-response  true
                                                       :requests-counter 0
                                                       :request-depth    (- request-depth 1)
                                                       :complete-atom    (atom nil)}
                                                   ctx-atom)
                              (cons f args)]))))

;;# reply

(defn reply
  "Reply to a request:

     ctx-atom - Defaults to *context-atom*.
     v        - The response.

No response is sent if the operating context is for a signal rather
than for a request or request-promise.

Once reply or exception-reply is called, requests can not be sent, nor will
responses be processed.

The reply function can only be used within the scope of a context map."

  ([v] (reply *context-atom* v))
  ([ctx-atom v]
   (let [complete-atom (context-get ctx-atom :complete-atom)]
     (if (compare-and-set! complete-atom nil true)
       (let [src-ctx-atom (context-get ctx-atom :src-ctx-atom)
             prom (context-get ctx-atom :promise)]
         (cond
           src-ctx-atom (let [fr (context-get ctx-atom :reply)
                              src-agent (:agent @src-ctx-atom)]
                          (send src-agent process-response [src-ctx-atom (list fr v)]))
           prom (deliver prom v)))))))

;;# exception-processor

(defn- exception-processor
  "Processes an exception response by simply rethrowing the exception:

     exception   - The exception thrown while
                   processing a request."

  [exception]
  (throw exception))

;;# exception-reply

(defn exception-reply
  "Pass an exception to the source which invoked the request, if any:

     ctx-atom - Defaults to *context-atom*.
     exception - The exception.

Once reply or exception-reply is called, requests can not be sent, nor will
responses be processed.

No response is sent if the current operating context is for a signal
rather than for a request. Rather, the exception is simply thrown.

The exception-reply function can only be used within the scope of a context map."

  ([exception] (exception-reply *context-atom* exception))
  ([ctx-atom exception]
   (let [complete-atom (context-get ctx-atom :complete-atom)]
     (if (compare-and-set! complete-atom nil true)
     (let [src-ctx-atom (context-get ctx-atom :src-ctx-atom)
           prom (context-get ctx-atom :promise)]
       (cond
         src-ctx-atom (let [src-agent (:agent @src-ctx-atom)]
                        (send src-agent process-response
                              [src-ctx-atom (list exception-processor exception)]))
         prom (deliver prom exception)
         :else (throw exception)))))))

;;# request-promise

(defn request-promise
  "Sends a function to an agent and returns a promise for the result:

     ag   - The target agent.
     f    - The function passed to the agent.
     args - Arguments passed to the function.

The function f is invoked with the target agent's value
pre-pended to its list of args.

The request-promise function can not be used within the scope of a context map."

  [ag f & args]
  (if *context-atom*
    (throw (Exception. "Illegal use of request-promise within the scope of a context map.")))
  (let [prom (promise)]
    (send ag process-request [(create-context-atom ag {:requests-counter 0
                                                       :request-depth    Integer/MAX_VALUE
                                                       :ensure-response  true
                                                       :promise          prom
                                                       :complete-atom    (atom nil)}
                                                   *context-atom*)
                              (cons f args)])
    prom))

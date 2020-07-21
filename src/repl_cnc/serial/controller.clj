(ns repl-cnc.serial.controller
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [repl-cnc.serial.proto :as serial]))

;; this isn't using the channel! It can pump a chan full now...
;; these shouldn't really be here
;; or maybe they should? The gcode is the part I don't like. I could pull it from the gcode ns
(defn abort [cnc-controller]
  (.writeBytes (:port (:cnc-port cnc-controller)) (.getBytes "!") (count (.getBytes "!"))))

(defn reset [cnc-controller]
  (.writeBytes (:port (:cnc-port cnc-controller)) (byte-array (byte 24)) 1))

(defn status [cnc-controller]
  (serial/send-command cnc-controller "?"))

;; improve naming in here!
(defrecord CNCController [cnc-port controller ctrl-ch work-ch]
  component/Lifecycle
  (start [cnc-controller]
    (let [ctrl-ch (async/chan)
          work-ch (async/chan 1024)]
      (-> (assoc cnc-controller :ctrl-ch ctrl-ch)
          (assoc :work-ch work-ch))))

  (stop [cnc-controller]
    (async/>!! ctrl-ch :stop)
    (async/close! ctrl-ch)
    (async/close! work-ch)
    (dissoc cnc-controller :controller :ctrl-ch :work-ch :cnc-port))

  serial/CNCCommandSender
  (send-command [cnc-controller data]
    ;; started protection
    (serial/write-data cnc-port data)
    ;; waiting for the read like this can block the serial port and stop me from aborting a job!
    ;; but it prevents too much work from being sent and dropped by the board
    (loop [response (serial/read-data cnc-port)]
      ;; if the response is blank keep trying, else add in success/error handling
      (if (empty? response)
        (do
          (Thread/sleep 10)
          (recur (serial/read-data cnc-port)))
        response)))

  serial/CNCJobExecutor
  (execute-job [cnc-controller job]
    (async/go
      ;; protect to make sure component is started
      ;; needs to listen to a ctrl-ch to be stoppable
      (doseq [gcode job]
        (doseq [response-chunk (serial/send-command cnc-controller gcode)]
          (print response-chunk))
        (println)))))

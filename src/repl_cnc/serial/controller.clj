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

(defn start-controller [cnc-controller ctrl-ch work-ch]
  (async/go-loop [[data chan] (async/alts! [ctrl-ch work-ch])]
    ;; maybe I should make this listen to the ctrl-ch and push abort/reset
    ;; to the cnc that way...
    (when (= chan work-ch)
      (serial/send-command cnc-controller data)
      (recur (async/alts! [ctrl-ch work-ch])))))

;; improve naming in here!
(defrecord CNCController [cnc-port controller ctrl-ch work-ch]
  component/Lifecycle
  (start [cnc-controller]
    (let [ctrl-ch (async/chan)
          work-ch (async/chan 1024)]
      (-> (assoc cnc-controller :ctrl-ch ctrl-ch)
          (assoc :work-ch work-ch)
          (assoc :controller (start-controller cnc-controller ctrl-ch work-ch)))))

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
    (serial/read-data cnc-port))

  serial/CNCJobExecutor
  (execute-job [cnc-controller job]
    (async/go
      ;; protect to make sure component is started
      (doseq [gcode job]
        (async/>! work-ch gcode)))))

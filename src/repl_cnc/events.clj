(ns repl-cnc.events
  (:import [com.fazecast.jSerialComm SerialPort SerialPortDataListener])
  (:require [clojure.java.io :as io]))

(defn basic-listener [event]
  (clojure.pprint/pprint (bean event))
  #_(with-open [cnc-stream (.getInputStream (.getSerialPort event))
              data (java.io.ByteArrayOutputStream.)]
    (io/copy cnc-stream data)
    (print (.toString data))))

(defn ->listener [listener-fn]
  (proxy [SerialPortDataListener] []
    (getListeningEvents [] SerialPort/LISTENING_EVENT_DATA_AVAILABLE)
    (serialEvent [event]
      (listener-fn event))))

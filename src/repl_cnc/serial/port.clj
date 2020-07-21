(ns repl-cnc.serial.port
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [repl-cnc.serial.proto :as serial])
  (:import [com.fazecast.jSerialComm SerialPort]
           [java.util Calendar]
           [java.text SimpleDateFormat]))

;; interface w/ serial library, read & write fns in here
(def baud-rates
  {:grbl 115200}) ; maybe a multi would be better? Other places may care about what machine/board you're using...

(def mode-flag-lookup
  {:non-blocking SerialPort/TIMEOUT_NONBLOCKING
   :read-semi-blocking SerialPort/TIMEOUT_READ_SEMI_BLOCKING
   :read-blocking SerialPort/TIMEOUT_READ_BLOCKING
   :write-semi-blocking SerialPort/TIMEOUT_WRITE_SEMI_BLOCKING
   :write-blocking SerialPort/TIMEOUT_WRITE_BLOCKING
   :event-data-written SerialPort/LISTENING_EVENT_DATA_WRITTEN
   :event-data-received SerialPort/LISTENING_EVENT_DATA_RECEIVED
   :event-data-available SerialPort/LISTENING_EVENT_DATA_AVAILABLE})

(defn list-ports []
  (->> (SerialPort/getCommPorts)
       (map (fn [port]
              {:systemName (.getSystemPortName port)
               :description (.getPortDescription port)}))))

(defn get-port [system-name]
  (->> (SerialPort/getCommPorts)
       (filter #(= (.getSystemPortName %) system-name))
       first))

(defn mode-or [mode-flags]
  (reduce bit-or 0 mode-flags))

(defn parse-mode-flags [mode-flags]
  ;; use a dict to convert from keywords to SerialPort flags
  (-> (map mode-flag-lookup mode-flags)
      mode-or))

(defrecord CNCPort [port mode-flags read-timeout write-timeout baud-rate]
  component/Lifecycle
  (start [cnc-port]
    ;; info log
    (.setBaudRate port baud-rate)
    (if (not (.openPort port))
      (throw (ex-info "Failed to open serial port"
                      {:port (bean port)
                       :baud-rate baud-rate})))
    (.setComPortTimeouts port
                         (parse-mode-flags mode-flags)
                         read-timeout
                         write-timeout)
    cnc-port)

  (stop [cnc-port]
    ;; info log
    (if (not (.closePort port))
      (throw (ex-info "Failed to close serial port"
                      {:port (bean port)})))
    {})

  serial/CNCPortReader
  (read-data [cnc-port]
    (let [lines (transient [])]
      (while (not (zero? (.bytesAvailable port)))
        (let [available (.bytesAvailable port)
              bytes (byte-array available)]
          (.readBytes port bytes available)
          (conj! lines (String. bytes))
          (Thread/sleep 20)))
      (persistent! lines)))

  serial/CNCPortWriter
  (write-data [cnc-port data]
    ;; debug log (should use actual logging...)
    (println "[" (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSZ") (.getTime (Calendar/getInstance))) "] Writing:" data)
    (let [data-bytes (.getBytes (str data "\n"))]
      (.writeBytes port data-bytes (count data-bytes)))))

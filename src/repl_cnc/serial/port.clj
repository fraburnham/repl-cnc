(ns repl-cnc.serial.port
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [repl-cnc.serial.proto :as serial])
  (:import [com.fazecast.jSerialComm SerialPort]))

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

(defn bytes-to-read [port]
  "Return bytes waiting or 1"
  (if (zero? (.bytesAvailable port))
    1
    (.bytesAvailable port)))

(defn join-byte-arrays [a b]
  (let [r (byte-array (+ (count a) (count b)) a)]
    (doall
     (for [i (range (count b))]
       (aset-byte r (+ i (count a)) (nth b i))))
    r))

(defn end-of-data? [data-bytes]
  (let [data-str (String. data-bytes)]
    (or (.contains data-str "ok\r\n")
        (.contains data-str "error"))))

(defn read-bytes [port & last-bytes] ; this strategy is working for now. It blocks until the board confirms with "ok\r\n" (or "error <some message>")
  (let [bytes-waiting (bytes-to-read port)
        data-bytes (byte-array bytes-waiting)]
    (.readBytes port data-bytes bytes-waiting)
    ;; this is so ugly. I think I'm missing some better functional thing
    (let [joined-bytes (join-byte-arrays (first last-bytes) data-bytes)]
      (if (end-of-data? joined-bytes)
        joined-bytes
        (read-bytes port joined-bytes)))))

(defn write-bytes [port data-bytes]
  (.writeBytes port data-bytes (count data-bytes)))

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
    ;; debug log
    (String. (read-bytes port)))

  serial/CNCPortWriter
  (write-data [cnc-port data]
    ;; debug log
    (println "Writing:" data)
    (write-bytes port (.getBytes (str data "\n")))))

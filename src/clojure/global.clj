(ns global
  (:import (java.util.concurrent Executors)
	   (javax.imageio ImageIO)
	   (java.io File)
	   (java.awt Image)))

(def task-executor (agent nil))
(def short-task-executor (agent nil))
(def print-executor (agent nil))

(def cached-executor (Executors/newCachedThreadPool))

(def fixed-executor (Executors/newFixedThreadPool 2))

(defmacro long-task [& body]
  `(send task-executor 
	 (fn [_#]
	   (.execute cached-executor (fn [] ~@body)))))

(defmacro short-task [& body]
  `(send short-task-executor 
	 (fn [_#]
	   (.execute fixed-executor (fn [] ~@body)))))

(defmacro print-task [& body]
  `(send print-executor 
	 (fn [_#]
	   (println ~@body))))

(defmacro once-short [& body] `(short-task ~@body))
(defmacro once-long [& body] `(long-task ~@body))
(defmacro once [& body] `(once-short ~@body))


(def app (ref nil))

(def copied-curves (ref []))

(def glove-image (.getScaledInstance (ImageIO/read (File. "resources/glove.png")) 24 24 Image/SCALE_DEFAULT))

(def interactive (ref false))

(defn enable-interaction []
  (dosync (ref-set interactive true)))

(defn disable-interaction []
  (dosync (ref-set interactive false)))

(defmacro deflogger [name-space]
  (list 'let ['logger (list 'org.slf4j.LoggerFactory/getLogger (str name-space))]
	'(defn- info [msg] (global/once-short (.info logger msg)))
	'(defn- warn [msg] (global/once-short (.warn logger msg)))
	'(defn- debug [msg] (global/once-short (.debug logger msg)))
	(list 'def (symbol (str name-space "-info")) 'info)
	(list 'def (symbol (str name-space "-warn")) 'warn)
	(list 'def (symbol (str name-space "-debug")) 'debug)))
(ns tests.tgui
  (:require app.controller app.model
	    lasfile.controller lasfile.model
	    editor.controller editor.model
	    lasso)
  (:use util gutil global))

(defn fail [] (assert false))

(defn wait-for [[millis interval msg] fun]
  (let [start (System/currentTimeMillis)
	probe (ref false)
	result (loop []
		 (fun probe)
		 (cond 
		   @probe true
		   (> (System/currentTimeMillis) (+ start millis)) false
		   :else 
		   (do
		     (Thread/sleep interval)
		     (recur))))]
    (if (= true result)
      (println "success : " msg)
      (do 
	(println "failure : " msg)
	(fail)))))

(defmacro swing-probe [& body]
  `(fn [probe#]
     (swing-sync
      (let [result# (do ~@body)]
	(when result#
	  (ref-set probe# true))))))

(defn test-add-lasfile []
  (app.controller/async-open-main)
  (let [dollie (lasso/load-lasfile "las_files/dollie.las")]
    (lasfile.controller/add-lasfile dollie)
    (wait-for [10000 100 "added all curves"]
      (swing-probe 
       (= (count (:curves dollie)) 
	  (.. (lasfile.model/get-selected-curve-list) (getModel) (getSize))))))
  (app.controller/close-main))

(defn test-open-editor []
  (app.controller/async-open-main)
  (let [dollie (lasso/load-lasfile "las_files/dollie.las")
	frame (editor.controller/open-curve-editor dollie (take 2 (:curves dollie)))]
    (wait-for [10000 100 "curve editor contains frame and curves"]
      (swing-probe 
       (and (contains? @editor.model/frame-data frame)
	    (contains? (get @editor.model/frame-charts frame) (first (:curves dollie)))
	    (contains? (get @editor.model/frame-charts frame) (nth (:curves dollie) 1))
	    (not (contains? (get @editor.model/frame-charts frame) (nth (:curves dollie) 2))))))))

(defn test-sync-curve-with-table []
  (app.controller/async-open-main)
  (let [test1 (lasso/load-lasfile "las_files/test.las")
	frame (editor.controller/open-curve-editor test1 (take 2 (:curves test1)))
	index 0]
    (swing
      (let [table (get-in @editor.model/frame-widgets [frame :table])]
	(.setValueAt (.getModel table) 10 (editor.model/index-to-row 0 table) 1)))
    (wait-for [10000 100 "dirty-curve(0) == 10 after syncing with table"]
      (swing-probe
       (let [curve (first (:curves test1))
	     dirty-curve (get-in @editor.model/frame-charts [frame curve :dirty-curve])]
	 (= (nth (:data dirty-curve) 0) 10))))))


(defn run-tests []
  (test-add-lasfile)
  (test-open-editor)
  (test-sync-curve-with-table)
  (System/exit 0))

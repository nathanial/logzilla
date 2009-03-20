(ns core.las
  (:use util)
  (:import (core DefaultCurve)
	   (java.util LinkedList)))

(defn get-curve [name curves]
  (find-first #(= (.getMnemonic %) name) curves))

(defn largest-index [curves]
  (.getIndex
   (reduce 
    (fn [c1 c2]
      (let [i1 (.getIndex c1)
	    i2 (.getIndex c2)]
	(if (> (count (.getLasData i1)) (count (.getLasData i2)))
	  c1
	  c2)))
    curves)))

(defn large-to-small [coll]
  (reverse (sort coll)))

(defn small-to-large [coll]
  (sort coll))

(defn abs [a]
  (if (< a 0)
    (* -1 a)
    a))

(defn start-offset [primary-index curve-index sample-rate]
  (let [primary-index (small-to-large primary-index)
	curve-index (small-to-large curve-index)]
    (/ (abs (- (first primary-index) (first curve-index)))
       sample-rate)))

(defn end-offset [primary-index curve-index sample-rate]
  (let [primary-index (small-to-large primary-index)
	curve-index (small-to-large curve-index)]
    (/ (abs (- (last primary-index) (last curve-index)))
       sample-rate)))

(defn isample-rate [index]
  (let [idata (.getLasData index)
	first (nth (seq idata) 0)
	second (nth (seq idata) 1)
	rate (- first second)]
    (if (< rate 0)
      (* -1 rate)
      rate)))

(defn sample-rate [curve]
  (let [index (.getIndex curve)]
    (isample-rate index)))

(defn adjust-curve [primary-index curve]
  (let [pdata (large-to-small (.getLasData primary-index))
	cidata (large-to-small (.getLasData (.getIndex curve)))
	srate (sample-rate curve)
	start-padding (repeat (start-offset pdata cidata srate) Double/NaN)
	end-padding (repeat (end-offset pdata cidata srate) Double/NaN)]
    
    (new DefaultCurve 
	 (.getDescriptor curve)
	 primary-index
	 (new LinkedList (concat start-padding 
				 (.getLasData curve)
				 end-padding)))
    ))

(defn merge-data [index datas]
  (for [i (range 0 (count (.getLasData index)))]
    (merge-row ())))

(defn merge-curves [index curves]
  (guard (all-same (map #(.getDescriptor %) curves))
	 "cannot merge curves with different descriptors")
  (guard (all-same (map #(count #(.getLasData %) curves)))
	 "all curves must be the same length (or be appropriated padded)")
  (guard (= (count index) (count (.getLasData (first curves))))
	 "curve data length must equal index length")
  (let [prototype (first curves)]
    (new DefaultCurve
	 (.getDescriptor prototype)
	 index
	 (merge-data (map #(.getLasData %) curves))
	 )))


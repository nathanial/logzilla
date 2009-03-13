(ns tparser  
  (:use util data las))

(load "parser")
(refer 'private-parser)
(refer 'parser)

(defn test-zapping []
  (with-input "abc."
    (assert (seq-eq (upto ".") "abc")))
  (with-input well-header-text
    (drop-line)
    (assert (seq-eq (grab-line) "~Well"))
    (goto-line "UWI")
    (assert (seq-eq (grab-line) "UWI.      : UNIQUE WELL ID"))
    (assert (seq-eq (upto ".") "API")))
  (with-input well-header-text
    (goto-line "DATE")
    (assert (seq-eq "DATE" (zapto ".")))
    (assert (seq-eq "" (zapto " ")))
    (assert (seq-eq "Monday, January 26 2009 14:04:02"
		    (limit-line (zapto-last ":"))))))

(defn test-descriptors []
  (let [read-descriptor (fn [key] (with-input (key descriptors-text) (parse-descriptor)))
	compare-descriptor (fn [a b c d e] 
			     (println "testing " a)
			     (assert (= a (struct descriptor b c d e))))
	dept (read-descriptor :dept)
	net-gross (read-descriptor :net-gross)
	facies (read-descriptor :facies)
	porosity (read-descriptor :porosity)
	gamma (read-descriptor :gamma)
	depth (read-descriptor :depth)
	start (read-descriptor :start)
	stop (read-descriptor :stop)
	date (read-descriptor :date)]
    (compare-descriptor dept "DEPT" "m" nil "DEPTH")
    (compare-descriptor net-gross "NetGross" nil nil "NetGross")
    (compare-descriptor facies "Facies" nil nil "Facies")
    (compare-descriptor porosity "Porosity" "m3/m3" nil "Porosity")
    (compare-descriptor gamma "Gamma" "gAPI" nil "Gamma")
    (compare-descriptor depth "DEPTH" "m" nil "trend")
    (compare-descriptor start "STRT" "m" "1499.8790000" nil)
    (compare-descriptor stop "STOP" "m" "2416.3790000" nil)
    (compare-descriptor date "DATE" nil "Monday, January 26 2009 14:04:02" "DATE")))

(defn test-version-header []
  (with-input version-header-text
    (let [vh (parse-version-header)
	  ds (:descriptors vh)]
      (assert (= (:data (get-descriptor vh "VERS")) "2.0"))
      (assert (= (:data (get-descriptor vh "WRAP")) "NO")))))

(defn test-well-header []
  (with-input well-header-text
    (let [wh (parse-well-header)
	  ds (:descriptors wh)
	  date (find-first #(= "DATE" (:mnemonic %)) ds)]
      (assert (= (:data date) "Monday, January 26 2009 14:04:02"))
      (assert (= (:description date) "DATE")))))

(defn test-curve-header []
  (with-input curve-header-text
    (let [ch (parse-curve-header)
	  ds (:descriptors ch)]
      (assert (= 6 (count ds)))
      (assert (every? #(is-in ["DEPT" "NetGross" "Facies" "Porosity" "Gamma" "DEPTH"] %)
		      (map #(:mnemonic %) ds))))))

(def my-curve-header (with-input curve-header-text (parse-curve-header)))

(defn test-las-curves []
  (with-input las-data-text
    (let [[index curves] (parse-curves my-curve-header)]
      (assert (= 5 (count curves)))
      (assert (not (nil? index)))
      (assert (every? #(= 9 (count (:data %))) curves)))))

(defn test-las-file []
  (let [lf (parse-las-file (slurp "las_files/test.las"))
	dept (:index lf)
	gamma (get-curve lf "Gamma")
	porosity (get-curve lf "Porosity")]
    (assert (= 1501.629 (nth (:data dept) 0)))
    (assert (= "gAPI" (:unit gamma)))
    (assert (= "m3/m3" (:unit porosity)))
    (assert (= "DEPTH" (:description dept)))

    (let [hs (headers lf)]
      (assert (= (count hs) (count header-types)))
      (doseq [htype header-types]
	(assert (not (nil? (get lf htype))))))))      

(defn test-dollie []
  (let [lf (parse-las-file (slurp "las_files/dollie.las"))
	dept (:index lf)
	wtoc (get-curve lf "WTOC")]
    (assert (not (nil? dept)))
    (assert (not (nil? wtoc)))
    (assert (= 7800 (nth (:data dept) 0)))
    (assert (= 6680 (last (:data dept))))
    (assert (= "LBF/LBF" (:unit wtoc)))))

(defn test-x4 []
  (let [lf (parse-las-file (slurp "las_files/x4.las"))
	wh (:well-header lf)
	strt (:data (get-descriptor wh "STRT"))
	stop (:data (get-descriptor wh "STOP"))]
    (assert (not (nil? wh)))
    (assert (not (nil? strt)))
    (assert (not (nil? stop)))
    (assert (= strt "57.000000000"))
    (assert (= stop "5817.0000000"))))

(defn run-tests []
  (test-zapping)
  (println "finished zapping")
  (test-descriptors)
  (println "finished descriptors")
  (test-version-header)
  (println "finished version header")
  (test-well-header)
  (println "finished well header")
  (test-curve-header)
  (println "finished curve header")
  (test-las-curves)
  (println "finished las curves")
  (test-las-file)
  (println "finished las file")
  (test-dollie)
  (println "finished dollie")
  (time (test-x4))
  (println "finished x4"))
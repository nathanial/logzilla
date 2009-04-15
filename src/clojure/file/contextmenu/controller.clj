(ns file.contextmenu.controller
  (:use util gutil global file.contextmenu.view file.model)
  (:import (java.awt.event MouseEvent MouseAdapter)))

(defn edit [file-manager] 
  (fm-invoke :open-curve-editor file-manager))

(defn _merge [file-manager]
  (fm-invoke :open-curve-merger file-manager))

(defn copy [file-manager]
  (swing
   (let [file (fm-invoke :get-selected-file file-manager)
	 curves (fm-invoke :get-selected-curves (:curve-list @file))]
     (dosync (ref-set copied-curves curves)))))

(defn paste [file-manager]
  (swing
   (let [ccurves @copied-curves
	 file (fm-invoke :get-selected-file file-manager)]
     (long-task 
      (doseq [curve ccurves]
	(fm-invoke :add-curve file curve))))))

(defn _remove [file-manager] nil)

(defn init-listener [file-manager curve-list]
  (proxy [MouseAdapter] []
    (mouseClicked [e] 
		  (when (= (.getButton e) MouseEvent/BUTTON3)
		    (create-context-menu
		     curve-list (.getX e) (.getY e) 
		     {:edit (partial edit file-manager)
		      :merge (partial _merge file-manager)
		      :copy (partial copy file-manager)
		      :paste (partial paste file-manager)
		      :remove (partial _remove file-manager)
		      })))))
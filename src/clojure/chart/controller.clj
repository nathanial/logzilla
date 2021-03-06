(ns chart.controller
  (:use gutil util global lasso messages chart.render)
  (:import (org.jfree.chart ChartMouseListener ChartPanel)
	   (gui ChartUtil ImageUtil CurveIcon)
	   (org.jfree.chart.plot PlotOrientation)
	   (org.jfree.data Range)
	   (org.jfree.ui RectangleEdge)
	   (org.jfree.data.xy XYSeries XYSeriesCollection)
	   (org.jfree.chart.renderer.xy XYDifferenceRenderer StandardXYItemRenderer)
	   (org.jdesktop.swingx.graphics ShadowRenderer)
	   (java.awt.event MouseAdapter MouseMotionAdapter)
	   (java.awt.geom Point2D Point2D$Double)
	   (javax.imageio ImageIO)
	   (java.io File)
	   (java.awt.image BufferedImage)
	   (javax.swing JLabel ImageIcon SwingConstants)
	   (java.awt Toolkit Image Point Cursor Rectangle Color AlphaComposite Font)))

(declare save-chart update-percentage set-value)

(defstruct Chart
  :chart-panel
  :curves
  :dirty-curves
  :changes
  :dragged-entity
  :dragging-enabled
  :zooming-enabled
  :showing-points)

(defn min-depth [curve]
  (let [index-data (get-in curve [:index :data])]
    (reduce min index-data)))

(defn max-depth [curve]
  (let [index-data (get-in curve [:index :data])]
    (reduce max index-data)))

(defn xjava-2D-to-value [chart-panel x]
  (swing-getter
   (let [chart (.getChart chart-panel)
	 xaxis (.. chart (getPlot) (getRangeAxis))
	 value (.java2DToValue xaxis x (.getScreenDataArea chart-panel) RectangleEdge/TOP)]
     value)))

(defn yjava-2D-to-value [chart-panel y]
  (swing-getter
   (let [chart (.getChart chart-panel)
	 yaxis (.. chart (getPlot) (getDomainAxis))
	 value (.java2DToValue yaxis y (.getScreenDataArea chart-panel) RectangleEdge/TOP)]
     value)))

(defn fire-percentage-change [chart percentage]
  (fire :percentage-change chart {:percentage percentage :source chart}))

(defn retrieve-dataset [chart-panel]
  (swing-getter
   (.. chart-panel (getChart) (getPlot) (getDataset))))

(defn retrieve-series [chart-panel curve-index]
  (swing-getter
   (let [series (. (retrieve-dataset chart-panel) (getSeries))]
     (nth series curve-index))))

(defn get-chart-range [xaxis]
  (swing-getter
   (let [range (.getRange xaxis)
	 lower (.getLowerBound range)
	 upper (.getUpperBound range)]
     (abs (- upper lower)))))

(defn get-depth-range [curve]
  (let [mind (min-depth curve)
	maxd (max-depth curve)]
    (abs (- maxd mind))))

(defn get-scale [depth-range chart-range]
  (/ depth-range chart-range))

(defn get-unit [depth-range scale]
  (/ depth-range scale))

;impure
(defn get-extent [xaxis]
  (swing-getter
   (.. xaxis (getRange) (getLowerBound))))

;impure
(defn get-percentage [xaxis mind depth-range]
  (swing-getter
   (let [lower (.. xaxis (getRange) (getLowerBound))]
     (/ (- lower mind) depth-range))))

(def default-scale 10)

(defn dirty-curve [chart]
  (only (:dirty-curves @chart)))

;impure
(defn delta [x y entity]
  (swing-impure
   (let [bounds (.. entity (getArea) (getBounds))
	 bx (. bounds x)
	 by (. bounds y)]
     (let [d (sqrt (+ (pow (abs (- x by)) 2)
		      (pow (abs (- y bx)) 2)))]
       d))))

(defn in-range [x y entity]
  (< (delta x y entity) 10))

(defn closest [x y entities]
  (let [len (count entities)]
    (cond 
     (> len 1)
     (let [delta (partial delta x y)]
       (reduce 
	(fn [a b]
	  (if (< (delta a) (delta b))
	    a
	    b))
	entities))

     (= len 1) (first entities))))

(defn set-dragged-entity [chart event]
  (swing-observer
   (dosync
    (let [chart-panel (:chart-panel @chart)
	  insets (.getInsets chart-panel)
	  x (/ (- (.getX event) (. insets left)) (.getScaleX chart-panel))
	  y (/ (- (.getY event) (. insets top)) (.getScaleY chart-panel))
	  entities (.. chart-panel (getChartRenderingInfo) (getEntityCollection) (getEntities))
	  nearest-entities (if (:showing-points @chart)
			     (filter #(in-range y x %) entities)
			     (filter #(in-range x y %) entities))
	  chosen (if (:showing-points @chart)
		   (closest y x nearest-entities)
		   (closest x y nearest-entities))]
      (alter chart assoc :dragged-entity chosen)))))

(defn set-anchor [chart event]
  (swing-observer
   (dosync
    (let [chart-panel (:chart-panel @chart)
	  plot (.. chart-panel (getChart) (getPlot))]
      (alter chart assoc :anchor 
	     [(.getX event) (.getY event) 
	      (.. plot (getRangeAxis) (getRange))
	      (.. plot (getDomainAxis) (getRange))])))))

(defn release-anchor [chart]
  (alter chart assoc :anchor nil))

(defn release-dragged-entity [chart]
  (alter chart assoc :dragged-entity nil))

(defn chart-press-listener [chart]
  (proxy [MouseAdapter] []
       (mousePressed [event]
		     (swing-mutator
		      (dosync 
		       (when (:dragging-enabled @chart)
			 (set-dragged-entity chart event))
		       (when (:panning-enabled @chart)
			 (set-anchor chart event)))))
       (mouseReleased [event]
		      (swing-mutator
		       (dosync 
			(when (:dragging-enabled @chart)
			  (release-dragged-entity chart))
			(when (:panning-enabled @chart)
			  (release-anchor chart))
			(save-chart chart))))))

(defn center-on [chart x y]
  (swing-agent
   (let [{:keys [chart-panel dirty-curves anchor]} @chart]
     (when anchor
       (let [[anchor-x anchor-y anchor-xrange anchor-yrange] anchor
	     yaxis (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	     ydelta (- (yjava-2D-to-value chart-panel y)
		       (yjava-2D-to-value chart-panel anchor-y))

	     xaxis (.. chart-panel (getChart) (getPlot) (getRangeAxis))
	     xdelta (- (xjava-2D-to-value chart-panel anchor-x)
		       (xjava-2D-to-value chart-panel x))
	     new-xrange (Range. (+ (.getLowerBound anchor-xrange) xdelta)
				(+ (.getUpperBound anchor-xrange) xdelta))
	     new-yrange (Range. (+ (.getLowerBound anchor-yrange) ydelta)
				(+ (.getUpperBound anchor-yrange) ydelta))]
	 (.setRange yaxis new-yrange)
	 (.setRange xaxis new-xrange)
	 (update-percentage chart))))))

(defn chart-drag-listener [chart]
  (proxy [MouseMotionAdapter] []
    (mouseDragged [event]
		  (dosync
		   (cond
		    (and (:dragging-enabled @chart) (:dragged-entity @chart))
		    (let [dragged-entity (:dragged-entity @chart)
			  chart-panel (:chart-panel @chart)
			  curve (.getSeriesIndex dragged-entity)]
		      (when dragged-entity
			(swing-agent
			 (let [series (retrieve-series chart-panel curve)
			       index (.getItem dragged-entity)
			       value (xjava-2D-to-value chart-panel (.getX event))]
			   (when (not (or (.isNaN value) (.isInfinite value)))
			     (set-value chart {:curve curve :index index} value))))))
		    
		    (:panning-enabled @chart)
		    (center-on chart (.getX event) (.getY event)))))))

(defn custom-chart-panel [chart jfree-chart]
  (let [chart-panel (proxy [ChartPanel] [jfree-chart false false false false false]
		      (zoom [rect]
			    (proxy-super zoom rect)
			    (update-percentage chart)))]
    (doto chart-panel
      (.setMouseZoomable false)
      (.addMouseListener (chart-press-listener chart))
      (.addMouseMotionListener (chart-drag-listener chart)))))

;; main controller

(defn update-percentage [chart]
  (swing-agent
   (let [{:keys [chart-panel dirty-curves]} @chart
	 xaxis (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	 exemplar (first dirty-curves)
	 mind (min-depth exemplar)
	 depth-range (get-depth-range exemplar)
	 percentage (get-percentage xaxis mind depth-range)]
     (fire-percentage-change chart percentage))))

(defmulti show-percentage (fn [x y] 
			    (cond 
			     (and (sequential? x) (number? y)) [:charts :percentage]
			     (and (sequential? x) (= (class y) clojure.lang.PersistentArrayMap)) [:charts :event]
			     (and (not (sequential? x)) (number? y)) [:chart :percentage]
			     (and (not (sequential? x)) (= (class y) clojure.lang.PersistentArrayMap))  [:chart :event]
			     )))

(defmethod show-percentage [:charts :percentage] [charts percentage]
  (doseq [chart charts]
    (show-percentage chart percentage)))

(defmethod show-percentage [:charts :event] [charts event]
  (doseq [chart charts]
    (show-percentage chart event)))

(defmethod show-percentage [:chart :event] [chart event]
  (show-percentage chart (:percentage event)))

(defmethod show-percentage [:chart :percentage] [chart percentage]
  (swing-agent
   (let [{:keys [chart-panel dirty-curves]} @chart]
     (fire-percentage-change chart percentage)
     (let [xaxis (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	   exemplar (first dirty-curves)
	   chart-range (get-chart-range xaxis)
	   depth-range (get-depth-range exemplar)
	   mind (min-depth exemplar)
	   scale (get-scale depth-range chart-range)
	   unit (get-unit depth-range scale)
	   lower (+ mind (* percentage depth-range))
	   upper (+ lower unit)]
       (.setRange xaxis (Range. lower upper))
       (.repaint chart-panel)))))

(defmulti init-chart-panel (fn [x y] 
			     (cond 
			       (sequential? y) :curves
			       :else :curve)))

(defmethod init-chart-panel :curves [chart-ref curves]
  (let [jfree-chart (create-chart curves)
	chart-panel (custom-chart-panel chart-ref jfree-chart)]
    chart-panel))

(defmethod init-chart-panel :curve [chart-ref curve]
  (let [jfree-chart (create-chart curve)
	chart-panel (custom-chart-panel chart-ref jfree-chart)]
    chart-panel))

(defn reset [chart]
  (dosync 
   (let [{:keys [chart-panel dirty-curves]} @chart
	 depth-ranges (doall (map get-depth-range dirty-curves))
	 depth-range (first depth-ranges)
	 mind (min-depth (first dirty-curves))
	 unit (get-unit depth-range default-scale)]
     (guard (all-same depth-ranges)
	    "all depth ranges must be equal to scale chart")
     (fire-percentage-change chart 0)
     (swing-agent
      (.restoreAutoRangeBounds chart-panel)
      (doto (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	(.setAutoRange false)
	(.setRange (Range. mind (+ mind unit))))))))

(defmulti init-chart (fn [x y] 
		       (cond 
			 (and (sequential? x) (sequential? y)) :multi
			 (and (not (sequential? x)) (not (sequential? y))) :single)))

(defmethod init-chart :multi [curves dirty-curves]
  (let [chart (ref nil)
	chart-panel (init-chart-panel chart dirty-curves)
	props (struct-map Chart 
		:chart-panel chart-panel
		:curves curves
		:dirty-curves dirty-curves)]
    (dosync (ref-set chart props))
    (add-receiver :percentage-change chart #(show-percentage chart %))
    (reset chart)
    chart))

(defmethod init-chart :single [curve dirty-curve]
  (let [chart (ref nil)
	chart-panel (init-chart-panel chart dirty-curve)
	props (struct-map Chart
		:chart-panel chart-panel
		:curves [curve]
		:dirty-curves [dirty-curve])]
    (dosync (ref-set chart props))
    (add-receiver :percentage-change chart #(show-percentage chart %))
    (reset chart)
    chart))

(defn set-scale [chart scale]
  (binding [default-scale scale]
    (reset chart)))

(defn scroll-up
  ([chart delta]
     (dosync 
      (let [{:keys [chart-panel dirty-curves]} @chart
	    xaxis (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	    exemplar (first dirty-curves)
	    depth-range (get-depth-range exemplar)
	    mind (min-depth exemplar)
	    percentage (get-percentage xaxis mind depth-range)]
	(show-percentage chart (+ percentage delta)))))
  ([chart]
     (scroll-up chart 1/100)))

(defn scroll-down 
  ([chart delta]
     (dosync 
      (let [{:keys [chart-panel dirty-curves]} @chart
	    xaxis (.. chart-panel (getChart) (getPlot) (getDomainAxis))
	    exemplar (first dirty-curves)
	    depth-range (get-depth-range exemplar)
	    mind (min-depth exemplar)
	    percentage (get-percentage xaxis mind depth-range)]
	(show-percentage chart (- percentage delta)))))
  ([chart]
     (scroll-down chart 1/100)))

(declare enable-zooming disable-zooming)

(defn enable-dragging [chart]
  (suppress 
    (dosync
     (disable-zooming chart)
     (alter chart assoc :dragging-enabled true))))

(defn disable-dragging [chart]
  (suppress (dosync (alter chart assoc :dragging-enabled false))))

(defn enable-zooming [chart]
  (suppress 
    (dosync 
     (disable-dragging chart)
     (let [chart-panel (:chart-panel @chart)]
       (alter chart assoc :zooming-enabled true)
       (swing-agent
	 (doto chart-panel
	     (.setMouseZoomable true)
	     (.setFillZoomRectangle false)))))))

(defn disable-zooming [chart]
  (suppress
    (dosync 
     (let [chart-panel (:chart-panel @chart)]
       (alter chart assoc :zooming-enabled false)
       (swing-agent
	 (doto chart-panel
	   (.setMouseZoomable false)))))))

(defn shade-difference [chart]
  (swing-reentrant
   (let [renderer (create-difference-renderer)
	 chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))]
     (.setRenderer plot renderer)
     (.repaint chart-panel))))

(defn unshade-difference [chart]
  (swing-reentrant
   (let [renderer (create-std-renderer)
	 chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))]
     (.setRenderer plot renderer)
     (.repaint chart-panel))))

(defn show-points [chart]
  (swing-reentrant
   (let [chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))
	 renderer (.getRenderer plot)]
     (.setBaseShapesVisible renderer true)
     (dosync (alter chart assoc :showing-points true)))))

(defn hide-points [chart]
  (swing-reentrant
   (let [chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))
	 renderer (.getRenderer plot)]
     (.setBaseShapesVisible renderer false)
     (dosync (alter chart assoc :showing-points false)))))

(defn toggle-points [chart]
  (swing-agent
   (dosync 
    (if (:showing-points @chart)
      (hide-points chart)
      (show-points chart)))))

(defn enable-panning [chart]
  (swing-reentrant
   (let [chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))]
     (.setCursor (:chart-panel @chart)
		 (Cursor. Cursor/HAND_CURSOR))
     (dosync (alter chart assoc :panning-enabled true)))))

(defn disable-panning [chart]
  (swing-reentrant
   (let [chart-panel (:chart-panel @chart)
	 plot (.. chart-panel (getChart) (getPlot))]
     (.setCursor chart-panel (Cursor/getDefaultCursor))
     (dosync (alter chart assoc :panning-enabled false)))))

(defn toggle-panning [chart]
  (swing-agent
   (dosync
    (if (:panning-enabled @chart)
      (disable-panning chart)
      (enable-panning chart)))))

(defn save-chart [chart]
  (dosync
   (let [{:keys [curves dirty-curves]} @chart]
     (doseq [[c d] (tuplize curves dirty-curves)]
       (alter c assoc :data (:data d))))))

(defn update-curve [chart curve-index curve]
  (swing-agent
   (let [chart-panel (:chart-panel @chart)
	 old-series (retrieve-series chart-panel curve-index)
	 new-series (XYSeries. "Series")
	 dataset (retrieve-dataset chart-panel)
	 index (:index curve)
	 cdata (:data curve)
	 idata (:data index)]
     (.removeSeries dataset old-series)
     (ChartUtil/addToSeries new-series idata cdata)
     (.addSeries dataset new-series)
     (.repaint chart-panel))))

(defn set-value [chart {:keys [curve index]} value]
  (dosync 
   (let [curve-index curve
	 data-index index
	 new-value (double value)
	 chart-panel (:chart-panel @chart)
	 curves (:curves @chart)]
     (alter chart assoc-in [:dirty-curves curve-index :data data-index] new-value)
     (fire :value-change chart {:curve-index curve-index
				:data-index data-index
				:value new-value})
     (swing-agent
      (let [series (retrieve-series chart-panel curve-index)]
	(.updateByIndex series data-index new-value))))))

(defn get-value [chart {:keys [curve index]}]
  (let [dirty-curve (nth (:dirty-curves @chart) curve)
	value (nth (:data dirty-curve) index)]
    value))
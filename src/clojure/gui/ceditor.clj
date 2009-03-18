(ns gui.ceditor
  (:use util gui.util))

(import '(java.awt BorderLayout Color)
	'(gui ChartUtil CustomChartPanel CustomJTable)
	'(javax.swing BorderFactory JPanel JSlider JWindow JFrame
		      JTable JScrollPane)
	'(javax.swing.table DefaultTableModel)
	'(javax.swing.event ChangeListener TableModelListener)
	'(javax.swing.border Border)
	'(javax.swing.event ChangeEvent ChangeListener)
	'(org.jfree.chart ChartFactory ChartPanel JFreeChart)
	'(org.jfree.chart.axis DateAxis)
	'(org.jfree.chart ChartMouseListener)
	'(org.jfree.chart.plot PlotOrientation XYPlot)
	'(org.jfree.chart.renderer.xy XYItemRenderer)
	'(org.jfree.data Range)
	'(org.jfree.data.general DatasetChangeEvent
				 DatasetChangeListener
				 DatasetUtilities
				 SeriesChangeListener)
	'(org.jfree.data.time Minute RegularTimePeriod
			      TimeSeries TimeSeriesCollection)
	'(org.jfree.data.xy AbstractXYDataset XYDataset XYSeries
			    XYSeriesCollection)
	'(org.jfree.ui ApplicationFrame RectangleInsets 
		       RefineryUtilities)
	'(net.miginfocom.swing MigLayout)
	'(java.awt Dimension Image))

(def dragged-item (agent nil))
(def slider-n 100)
(def scale-n 10)

(defn get-scale [range]
  (let [diff (- (.getUpperBound range) (.getLowerBound range))
	scale (/ diff scale-n)]
    scale))

(defn scale-value [scale value]
  (let [ratio (/ slider-n scale-n)]
    (* (/ value ratio) scale)))

(defn- create-slider-listener [depth-slider xaxis max-depth min-depth table]
  (let [scale (get-scale (.getRange xaxis))]
    (proxy [ChangeListener] []
      (stateChanged [event]
		    (let [value (.getValue depth-slider)
			  scaled (scale-value scale value)
			  lower (+ scaled min-depth)
			  upper (+ lower scale)]
		      (.setRange xaxis 
				 (if (> lower (- max-depth scale))
				   (- max-depth scale)
				   lower)
				 (if (> upper max-depth)
				   max-depth
				   upper))
		      (.showAtPercentage table (- 1 (/ value slider-n)))
		      )))))

(defn- create-depth-slider [min-depth max-depth]
  (let [slider (new JSlider 0 slider-n 0)]
    (.setOrientation slider JSlider/VERTICAL)
    slider))

(defn- create-table [curve]
  (let [index (.getIndex curve)
	table (new CustomJTable)
	model (new DefaultTableModel)]
    (doto model
	(.addColumn (.getMnemonic index) 
		    (into-array Object 
				(reverse (sort (.getLasData index)))))
	(.addColumn (.getMnemonic curve) 
		    (into-array Object 
				(reverse (sort (.getLasData curve))))))
    (.setModel table model)
    
    table))

(defn- index-to-row [index table]
  (- (dec (.getRowCount table)) index))

(defn- row-to-index [row table]
  (- (dec (.getRowCount table)) row))

(defn- sync-chart-with-table [table chart-panel row]
  (swing 
   (let [model (.getModel table)
	 chart (.getChart chart-panel)
	 series (first (.. chart (getPlot) (getDataset) (getSeries)))]
     (let [index (row-to-index row table)]
       (.updateByIndex series index (Double/valueOf (.getValueAt model row 1)))
       (.repaint chart-panel)))))

(defn- sync-table-with-chart [table chart-panel index]
  (let [model (.getModel table)
	chart (.getChart chart-panel)
	series (first (.. chart (getPlot) (getDataset) (getSeries)))
	row (index-to-row index table)
	item (.getDataItem series index)
	new-value (.getY item)]
    (swing
     (.setValueAt (.getModel table) new-value row 1)
     (.repaint table))))

(defn- create-table-model-listener [table chart-panel]
  (proxy [TableModelListener] []
    (tableChanged [e]
		  (guard (= (.getFirstRow e) (.getLastRow e))
			 "first row must equal last row")
		  (sync-chart-with-table table chart-panel (.getFirstRow e)))))

(defn- update-entity [chart-event]
  (send dragged-item
	(fn [entity]
	  (if entity
	    nil 
	    (.getEntity chart-event)))))

(defn- drag-entity [chart-panel table chart-event]
  (send dragged-item
	(fn [entity]
	  (when entity
	    (let [mouse-event (.getTrigger chart-event)
		  series (first (.. entity (getDataset) (getSeries)))
		  index (.getItem entity)
		  new-value (.java2DToValue chart-panel (.getX mouse-event))]
	      (swing 
	       (when (not (or (.isNaN new-value) (.isInfinite new-value)))
		 (.updateByIndex series index new-value)
		 (sync-table-with-chart table chart-panel index)
		 (.repaint chart-panel)))))
	  entity)))

(defn- create-chart-mouse-listener [chart-panel table]
  (proxy [ChartMouseListener] []
    (chartMouseClicked [e] (update-entity e))
    (chartMouseMoved [e] (drag-entity chart-panel table e))))

(defn open-curve-editor [curve]
  (let [index (.getIndex curve)
	depth-data (.getLasData index)
	min-depth (reduce min depth-data)
	max-depth (reduce max depth-data)
	depth-slider (create-depth-slider min-depth max-depth)
	chart (ChartUtil/createChart curve)
	table (create-table curve)
	table-pane (new JScrollPane table)
	chart-panel (new CustomChartPanel curve chart)
	main-panel (new JPanel (new MigLayout))
	frame (new JFrame (str (.getMnemonic curve) " Editor"))
	plot (.getPlot chart)
	x-axis (.getDomainAxis plot)
	scale (get-scale x-axis)]

    (.addChangeListener depth-slider 
			(create-slider-listener depth-slider
						x-axis max-depth
						min-depth table))

    (doto chart-panel
      (.setDomainZoomable false)
      (.setMouseZoomable false))
    
    (.addChartMouseListener chart-panel
			    (create-chart-mouse-listener chart-panel table))
    
    (.addTableModelListener (.getModel table)
			    (create-table-model-listener table chart-panel))
    
    (doto x-axis
      (.setAutoRange false)
      (.setRange (new Range min-depth (+ min-depth (* scale 1)))))
    
    (doto main-panel
      (.setPreferredSize (new Dimension 700 700))
      (.add depth-slider "pushy, growy")
      (.add table-pane "pushy, growy")
      (.add chart-panel "pushy, growy"))
    
    (doto table
      (.showAtPercentage 1))
    
    (doto frame
      (.add main-panel)
      (.pack)
      (.setVisible true))
    
    frame))
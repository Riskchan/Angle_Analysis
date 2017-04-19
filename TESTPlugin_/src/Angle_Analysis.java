import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import java.awt.event.*;
import java.util.*;

public class Angle_Analysis implements PlugIn, ActionListener, ItemListener{
	// Member variables
	Button m_bt_run;					// run button
	Button m_bt_set, m_bt_reset;		// set/reset button
	TextField m_txt_low, m_txt_high;	// higher/lower thresholds
	TextField m_txt_num_ang;			// number of angles
	TextField m_txt_srcx, m_txt_srcy;	// Source coordinate
	Choice m_cho_ctrg;					// Centering mode
	
	private double length_2d(double x1, double y1, double x2, double y2){
		return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
	}

	private double linear_val(double x, double y, double cx, double cy, double angle){
		return (y-cy)*Math.cos(angle) - (x-cx)*Math.sin(angle);
	}

	private double getIntersectionLength(double cx, double cy, double angle, Roi r){
		// Search through intersections
		ArrayList<Integer> idx = new ArrayList<Integer>();
		FloatPolygon pol = r.getFloatPolygon();
		int n = pol.npoints;
		for(int i=0; i<n-1; i++){
			double v1 = linear_val(pol.xpoints[i], pol.ypoints[i], cx, cy, angle);
			double v2 = linear_val(pol.xpoints[i+1], pol.ypoints[i+1], cx, cy, angle);
			// same side if v1*v2>0, otherwise opposite
			if(v1*v2 < 0){
				idx.add(i);
			}
		}

		// Finding desired direction and the farthest intersection
		ArrayList<Double> ix = new ArrayList<Double>();
		ArrayList<Double> iy = new ArrayList<Double>();
		for(int i=0; i<idx.size(); i++){
			double x1 = pol.xpoints[idx.get(i)];
			double y1 = pol.ypoints[idx.get(i)];

			double node_ang = Math.atan2(y1-cy, x1-cx);
			// atan2 returns -PI to PI -> 0 to 2*PI;
			if(node_ang < 0)
				node_ang += Math.PI*2;

			if(Math.abs(node_ang - angle) < Math.PI/2){
				double x2 = pol.xpoints[idx.get(i) + 1];
				double y2 = pol.ypoints[idx.get(i) + 1];

				// Solving
				// (x2-x1)(y-y1) = (y2-y1)(x-x1)
				// (y-cy)cos(angle) = (x-cx)sin(angle)
				double sin = Math.sin(angle);
				double cos = Math.cos(angle);
				double D = (y2-y1)*cos - (x2-x1)*sin;
				double a = cx*sin - cy*cos;
				double b = (y2-y1)*x1 - (x2-x1)*y1;

				// Calculating intersec and pushing into array list
				//IJ.log("ang=" + Math.atan2((b*sin - a*(y2-y1))/D-cy, (b*cos - a*(x2-x1))/D-cx));
				ix.add((b*cos - a*(x2-x1))/D);
				iy.add((b*sin - a*(y2-y1))/D);
				//IJ.log("(" + (b*cos - a*(x2-x1))/D + ", " + (b*sin - a*(y2-y1))/D + ")");
			}
		}

		// Finding farthest intersection
		double maxlen = 0;
		for(int i=0; i<ix.size(); i++){
			double len = length_2d(ix.get(i), iy.get(i), cx, cy);
			if(len>maxlen){
				maxlen = len;
			}
		}
		return maxlen;
	}

	private double[] getCenterOfMass(Roi roi){
		// Calculate center of mass
		Polygon poly = roi.getPolygon();
		double sumx = 0, sumy = 0;
		for(int i=0; i<poly.npoints; i++){
			sumx = sumx + poly.xpoints[i];
			sumy = sumy + poly.ypoints[i];
		}
		double[] com = new double[2];
		
		com[0] = sumx/poly.npoints;
		com[1] = sumy/poly.npoints;

		return com;
	}

	private void angle_analysis(int threshold_low, int threshold_high, int N) {
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;

		ImageProcessor ip = imp.getProcessor();

		/*
		// Find center of selection
		Roi roi_sel = imp.getRoi();
		if(null == roi_sel){
			IJ.showMessage("No area selected. ");
			return;
		}
        
		double[] com_sel = new double[2];
		com_sel = getCenterOfMass(roi_sel);

		// Set threshold and select
		Wand wand = new Wand(ip);
		wand.autoOutline((int)com_sel[0], (int)com_sel[1], threshold_low, threshold_high);
		PolygonRoi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.FREELINE);
		imp.setRoi(roi);
		*/
		
		Roi roi = imp.getRoi();

		// Calculate COM of Wand
		double[] com = new double[2];
		com = getCenterOfMass(roi);

		ArrayList<Double> max_arry = new ArrayList<Double>();
		for(int i=0; i<N; i++){
			double ang = 2*i*Math.PI/N;
			double len = getIntersectionLength(com[0], com[1], ang, roi);

			double vx = len * Math.cos(ang);
			double vy = len * Math.sin(ang);
			Line line = new Line(com[0], com[1], com[0]+vx, com[1]+vy);
			imp.setRoi(line);
			ProfilePlot prof = new ProfilePlot(imp);
			double max_prof = prof.getMax();
			max_arry.add(max_prof);
		}

		// Centering
		IJ.log("\\Clear");
		String ctr_mode = m_cho_ctrg.getSelectedItem();
		if (ctr_mode == "No centering"){
			for(int i=0; i<N; i++){
				IJ.log(String.valueOf(max_arry.get(i)));
			}
		}else if(ctr_mode == "Maximum centering"){
			// Find max
			int i_max = 0;
			double max = 0;
			for(int i=0; i<N; i++){
				if(max_arry.get(i) > max){
					i_max = i;
					max = max_arry.get(i);
				}
			}

			// Centering at max
			for(int i=0; i<N; i++){
				int i_new = (i_max + i + N/2) % N;
				IJ.log(String.valueOf(max_arry.get(i_new)));
			}
		}else if(ctr_mode == "Source centering"){
			// Calculate relative angle vector
			double vecx = Double.parseDouble(m_txt_srcx.getText()) - com[0];
			double vecy = Double.parseDouble(m_txt_srcy.getText()) - com[1];
			double len = Math.sqrt(vecx*vecx + vecy*vecy);
			vecx /= len;
			vecy /= len;
			
			// Find the closest angle
			int i_ang = 0;
			double min_len = 100000;
			for(int i=0; i<N; i++){
				double ang = 2*i*Math.PI/N;
				double angx = Math.cos(ang);
				double angy = Math.sin(ang);
				double rel_len = length_2d(angx, angy, vecx, vecy);
				if(rel_len < min_len){
					i_ang = i;
					min_len = rel_len;
				}
			}

			// Centering at relative direction
			for(int i=0; i<N; i++){
				int i_new = (i_ang + i + N/2) % N;
				IJ.log(String.valueOf(max_arry.get(i_new)));
			}
		}

		// Show polygon
		imp.setRoi(roi);
	}

	// Buttons pressed
    public void actionPerformed(ActionEvent e){
    	// Thresholds
		int low = Integer.parseInt(m_txt_low.getText());
		int high = Integer.parseInt(m_txt_high.getText());
		if (low > high){
			IJ.showMessage("Lower threshold must be lower than Higher threshold.");
			return;
		}
		
		// Number of angles
		int num_ang = Integer.parseInt(m_txt_num_ang.getText());
		if (num_ang < 0 || num_ang > 360){
			IJ.showMessage("Number of angles must be 0-360.");
			return;
		}
				
		// IJ
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();		

		Button b = (Button)e.getSource();
    	if (b==null){
			return;
    	}else if (b==m_bt_run){
    		angle_analysis(low, high, num_ang);
    	}else if (b==m_bt_set){
			ip.setThreshold(low, high, ImageProcessor.RED_LUT);
			imp.updateAndDraw();    		
    	}else if (b==m_bt_reset){
        	ip.resetThreshold();
			imp.updateAndDraw();
    	}
	}

    // Choice changed
    public void itemStateChanged(ItemEvent e) {
		Choice cho = (Choice)e.getItemSelectable();
		if (cho.getSelectedItem() == "Source centering"){
			m_txt_srcx.setEnabled(true);
			m_txt_srcy.setEnabled(true);
		}else{
			m_txt_srcx.setEnabled(false);
			m_txt_srcy.setEnabled(false);
		}
    }

    // Adding new label + component
    private void addLabeledComponent(String l, Frame frm, Component c){
        Panel p = new Panel();
		p.setLayout(new GridLayout(0, 2));
		p.add(new Label(l));
        p.add(c);
        frm.add(p);
    }
    
    public void run(String arg) {	
		
        Panel p = new Panel();
    	Frame frm = new Frame(new String("Angle analysis"));
    	frm.setSize(new Dimension(300,220));
    	frm.setLayout(new GridLayout(0, 1));
        frm.addWindowListener(new WindowAdapter() {
        	public void windowClosing(WindowEvent e) {
        		System.exit(0);
        	}
        });
        
        // Centering modes
        m_cho_ctrg = new Choice();
        m_cho_ctrg.add("No centering");
        m_cho_ctrg.add("Maximum centering");
        m_cho_ctrg.add("Source centering");
        m_cho_ctrg.addItemListener(this);
        addLabeledComponent("Centering mode:", frm, m_cho_ctrg);

        // Point source coordinate
        Panel pr = new Panel();
		pr.setLayout(new GridLayout(0, 2));
        m_txt_srcx = new TextField("0");
        m_txt_srcx.setEnabled(false);
        m_txt_srcy = new TextField("0");
        m_txt_srcy.setEnabled(false);
        pr.add(m_txt_srcx);
        pr.add(m_txt_srcy);
        addLabeledComponent("Source coordinate:", frm, pr);
        
        // Number of angles
        m_txt_num_ang = new TextField("360");
        addLabeledComponent("Number of angles:", frm, m_txt_num_ang);

        // Lower/Higher thresholds
        m_txt_low = new TextField("2200");
        addLabeledComponent("Lower threshold level:", frm, m_txt_low);
//        m_txt_low.addActionListener(this);
        
        m_txt_high = new TextField("10000");
		addLabeledComponent("Higher threshold level:", frm, m_txt_high);
        
		// Set/Reset threshold button
        p = new Panel();
		p.setLayout(new GridLayout(0, 2));

		m_bt_set = new Button("Set threshold");
		m_bt_set.addActionListener(this);
		p.add(m_bt_set);

		m_bt_reset = new Button("Reset threshold");
		m_bt_reset.addActionListener(this);
		p.add(m_bt_reset);
		frm.add(p);

		// Run button
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_run = new Button("Run");
		m_bt_run.addActionListener(this);
		p.add(m_bt_run);
		frm.add(p);

		// Show
		frm.show();
    }

}

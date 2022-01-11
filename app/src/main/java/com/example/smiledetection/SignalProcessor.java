package com.example.smiledetection;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.psambit9791.jdsp.transform.DiscreteFourier;
import com.github.psambit9791.jdsp.transform.Hilbert;

import org.apache.commons.math3.complex.Complex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class SignalProcessor extends AppCompatActivity {

    private int SAMPLE_LENGTH = 2048;
    private int NUMBER_OF_INITIAL = 5;
    private int NUMBER_OF_SAMPLE_POINTS = 20;
    private double[][] mixedSamplesPhase;
    private double[][] initialPoints;
    private double[] distances;
    private double[] center;
    private int countLow;
    private int displayTime = 0;
    int FREQ_BIN;
    private double thresBlink;
    private double thresSmile;
    private double thresTimeLow;
    private int iterator = 0;
    private double distPre = 0;
    private double distCur = 0;
    private double var;
    private boolean BinInitialized= false;
    private boolean ThresInitialized = false;

    //Write to file
    String filename="SleepSmile.txt", path="SmileDetection", content="";


    public SignalProcessor(){
        mixedSamplesPhase = new double[NUMBER_OF_INITIAL][SAMPLE_LENGTH];
        initialPoints = new double[NUMBER_OF_SAMPLE_POINTS][2];
        distances = new double[NUMBER_OF_SAMPLE_POINTS];
        center = new double[]{0.0, 0.0};
    }

    void initializeFreq_Bin(){
        FREQ_BIN = binIndex();
        BinInitialized = true;
        Log.e("initialization",String.format("FreqBin: %d", FREQ_BIN));
    }

    void initializeThresholds(){
        ThresInitialized = true;
        var = variance();
        thresBlink = 5*var;
        thresSmile = 5*var;
        thresTimeLow = 3;
        Log.e("initialization",String.format("variance: %f", var));
    }

    void FourierTransform(double[] sample, double[] record)
    {
        Hilbert h = new Hilbert(sample);
        h.hilbertTransform();
        double[][] analyticSample = h.getOutput();

        Hilbert g = new Hilbert(record);
        g.hilbertTransform();
        double[][] analyticRecord = g.getOutput();

        double[] mixedSignal = new double[SAMPLE_LENGTH];

        for(int i=0; i<SAMPLE_LENGTH; i++)
        {
            mixedSignal[i] = analyticRecord[i][0]*analyticSample[i][0] + analyticRecord[i][1]*analyticSample[i][1];
        }

        DiscreteFourier fft = new DiscreteFourier(mixedSignal);
        fft.dft();
        Complex[] mixedFftComplex = fft.returnComplex(true);

        if(iterator < NUMBER_OF_INITIAL && !BinInitialized){
            for(int i=0; i< SAMPLE_LENGTH/2; i++) {
                mixedSamplesPhase[iterator][i] = mixedFftComplex[i].getArgument();
            }
        }

        double amplitude = mixedFftComplex[FREQ_BIN].abs();
        double angle = mixedFftComplex[FREQ_BIN].getArgument();
        if(BinInitialized) {
            initialPoints[iterator][0] = amplitude * Math.cos(angle);
            initialPoints[iterator][1] = amplitude * Math.sin(angle);

            distances[iterator] = pointDistanceCartesian(initialPoints[iterator], center);
        }

        distPre = distCur;
        distCur = distances[iterator];
        iterator = (iterator+1)%NUMBER_OF_SAMPLE_POINTS;

        if(BinInitialized && ThresInitialized && iterator == 2*NUMBER_OF_INITIAL){
            double[] v = prattNewton(initialPoints);
            center[0] = v[0];
            center[1] = v[1];
        }
        //Log.e("SignalDimension", String.format("%d x %d",analyticSample.length, analyticSample[0].length));
        Log.e("SignalDistance", String.format("amplitude: %f, phase: %f, distance: %f", amplitude, angle, distCur));
        getFileTxt(amplitude+","+angle,"SmileSleep.txt");
    }

    //double[][] pointIQplane		//global variable, initialize it to size of 50

    int binIndex()
    {
        int bin = 1000;
        double maxRange = 0;

        for(int j = 0; j<SAMPLE_LENGTH/2; j++)
        {
            double minPhase = 4.00, maxPhase = -4.00;

            for(int i=0; i<NUMBER_OF_INITIAL; i++)
            {
                minPhase = Math.min(minPhase, mixedSamplesPhase[i][j]);
                maxPhase = Math.max(maxPhase, mixedSamplesPhase[i][j]);
            }

            if(maxRange < (maxPhase - minPhase))
            {
                maxRange = maxPhase - minPhase;
                bin = j;
            }
        }
        Log.e("Max Range", String.format("Max Range: %f", maxRange));
        return bin;
    }

    double pointDistancePolar(double r1, double theta1, double r2, double theta2)
    {
        return Math.sqrt(r1*r1 + r2*r2 - 2*r1*r2*Math.cos((theta1-theta2)*Math.PI));
    }

    double pointDistanceCartesian(double[] p1, double[] p2)
    {
        return Math.sqrt((p2[0]-p1[0])*(p2[0]-p1[0]) + (p2[1]-p1[1])*(p2[1]-p1[1]));
    }

    double variance()
    {
        // Compute mean (average of elements)
        double sum = 0;
        int n = NUMBER_OF_INITIAL;
        for (int i = NUMBER_OF_INITIAL; i < 2*NUMBER_OF_INITIAL; i++)
            sum += distances[i];

        double mean = (double)sum / (double)n;

        // Compute sum squared differences with mean.
        double sqDiff = 0;
        for (int i = 0; i < n; i++)
            sqDiff += (distances[i] - mean) * (distances[i] - mean);

        return (double)sqDiff / n;
    }

    int checkStatus()		//thresBlink, countLow, thresLow, thresSmile
    {
        int status = 2;

        if(distPre - distCur > thresBlink && countLow==0){
            countLow++;
        }
        else if((countLow != 0) && (Math.abs(distCur - distPre) < 3*var)){
            countLow++;
        }

        if(countLow >= thresTimeLow)
        {
            status = 0;
            Log.e("Status", "Sleeping");
            countLow = 0;
            displayTime = 0;
            return status;
        }

        if(distCur - distPre > thresSmile)
        {
            status = 1;
            displayTime = 0;
            Log.e("Status","Smiling");
            return status;
        }

        displayTime++;

        if(displayTime > 3){
            status = -1;
        }

        return status;
    }

    double[] signalToPoint(double[] signal)
    {
        // fourier domain conversion code

        double[] point = new double[2];
        //double amp, angle;
        //point[0] = amp*Math.cos();
        //point[1] = amp*Math.sin();


        return point;
    }


    /**
     * Pratt method (Newton style)
     *
     *  //double[n][2]
     *            containing n (<i>x</i>, <i>y</i>) coordinates
     * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
     *
     */
    public static double[] prattNewton(final double[][] points) {
        final int nPoints = points.length;
        if (nPoints < 3)
            throw new IllegalArgumentException("Too few points");
        final double[] centroid = getCentroid2D(points);
        double Mxx = 0, Myy = 0, Mxy = 0, Mxz = 0, Myz = 0, Mzz = 0;

        for (int i = 0; i < nPoints; i++) {
            final double Xi = points[i][0] - centroid[0];
            final double Yi = points[i][1] - centroid[1];
            final double Zi = Xi * Xi + Yi * Yi;
            Mxy += Xi * Yi;
            Mxx += Xi * Xi;
            Myy += Yi * Yi;
            Mxz += Xi * Zi;
            Myz += Yi * Zi;
            Mzz += Zi * Zi;
        }
        Mxx /= nPoints;
        Myy /= nPoints;
        Mxy /= nPoints;
        Mxz /= nPoints;
        Myz /= nPoints;
        Mzz /= nPoints;

        final double Mz = Mxx + Myy;
        final double Cov_xy = Mxx * Myy - Mxy * Mxy;
        final double Mxz2 = Mxz * Mxz;
        final double Myz2 = Myz * Myz;

        final double A2 = 4 * Cov_xy - 3 * Mz * Mz - Mzz;
        final double A1 = Mzz * Mz + 4 * Cov_xy * Mz - Mxz2 - Myz2 - Mz * Mz * Mz;
        final double A0 = Mxz2 * Myy + Myz2 * Mxx - Mzz * Cov_xy - 2 * Mxz * Myz * Mxy + Mz * Mz * Cov_xy;
        final double A22 = A2 + A2;

        final double epsilon = 1e-12;
        double ynew = 1e+20;
        final int IterMax = 20;
        double xnew = 0;
        for (int iter = 0; iter <= IterMax; iter++) {
            final double yold = ynew;
            ynew = A0 + xnew * (A1 + xnew * (A2 + 4 * xnew * xnew));
            if (Math.abs(ynew) > Math.abs(yold)) {
                System.out.println("Newton-Pratt goes wrong direction: |ynew| > |yold|");
                xnew = 0;
                break;
            }
            final double Dy = A1 + xnew * (A22 + 16 * xnew * xnew);
            final double xold = xnew;
            xnew = xold - ynew / Dy;
            if (Math.abs((xnew - xold) / xnew) < epsilon) {
                break;
            }
            if (iter >= IterMax) {
                System.out.println("Newton-Pratt will not converge");
                xnew = 0;
            }
            if (xnew < 0) {
                System.out.println("Newton-Pratt negative root:  x= " + xnew);
                xnew = 0;
            }
        }
        final double det = xnew * xnew - xnew * Mz + Cov_xy;
        final double x = (Mxz * (Myy - xnew) - Myz * Mxy) / (det * 2);
        final double y = (Myz * (Mxx - xnew) - Mxz * Mxy) / (det * 2);
        final double r = Math.sqrt(x * x + y * y + Mz + 2 * xnew);

        final double[] centreRadius = { x + centroid[0], y + centroid[1], r };
        return centreRadius;
    }

    /**
     * Find the centroid of a set of points in double[n][2] format
     *
     * @param points
     * @return
     */
    private static double[] getCentroid2D(final double[][] points) {
        final double[] centroid = new double[2];
        double sumX = 0;
        double sumY = 0;
        final int nPoints = points.length;

        for (int n = 0; n < nPoints; n++) {
            sumX += points[n][0];
            sumY += points[n][1];
        }

        centroid[0] = sumX / nPoints;
        centroid[1] = sumY / nPoints;

        return centroid;
    }
    private void getFileTxt(String content,String filename)
    {
        File txt=null;
        try {
            //Log.e("path123","welcome");
            FileWriter fw=null;
            BufferedWriter bw = null;
            File directory = SignalProcessor.this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            Log.e("path123",""+directory);
            txt = new File(directory, filename+".txt");
            Log.d("path",""+txt.exists());
            if (!txt.exists()) {
                txt.createNewFile();
            }
            fw = new FileWriter(txt.getAbsolutePath(), true);
            bw= new BufferedWriter(fw);
            bw.write(content);
            bw.close();


        }catch(Exception e) {
            //ok

        }
    }

}

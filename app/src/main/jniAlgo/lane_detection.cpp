#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/highgui/highgui.hpp"
#include <iostream>
#include <string>
#include <math.h>
#include "opencv2/video/video.hpp"

#include "lane_detection.h"

// #include <fstream>
// #include <stack>
// #include <ctime>
//#include <Windows.h>


#define WID 70
#define NPTSVAL 8 // number of possibe lane markers to be found in EACH band
#define NSCAN 16 // number of bands for processing the lane markers
#define NSCAN1 10 // for visualization

#define LCNST 150
#define RCNST 220
#define DRIFT 20

#define startIm 0
#define stopIm 10

#define N_PREV 15 // Number 
#define N_PREV_C 5
#define DRIFT_BANDS 12

int laneinitfunc();
std::vector<LaneDetectionResults *> *lanedetectAlgo(cv::Mat &,int,double,double,int,int);
cv::Mat inputImage;
cv::Mat outputImage;

int findPeak(cv::Mat);
int getLanePts(cv::Mat, cv::Mat, int,int,int,int);
cv::Point getIIPMPt(cv::Mat,cv::Point);


cv::Point peaksListPt1[8];
cv::Point finalPeakPts[8];

cv::Point pt11;
cv::Point pt22;
cv::Point pt33;
cv::Point pt44;

// void findAveVal_lane_params(Vec4f* lane_params);

using namespace std;
using namespace cv;

static int checkval;
static int laneptsL[NSCAN][N_PREV];
static int laneptsR[NSCAN][N_PREV];
static int laneptsL_ld[NSCAN][N_PREV];
static int laneptsR_ld[NSCAN][N_PREV];
static int laneptsL_rd[NSCAN][N_PREV];
static int laneptsR_rd[NSCAN][N_PREV];



static Vec4f lanesLBuff[N_PREV];
static Vec4f lanesRBuff[N_PREV];
static Vec4f laneLAve;
static Vec4f laneRAve;

//vector<vector<Point> > ptsiipmL_n;
//vector<vector<Point> > ptsiipmR_n;

static Point ptsiipmL[NSCAN];
static Point ptsiipmR[NSCAN]; // Total number of scan bands

static int contiL[stopIm];
static int contiR[stopIm];
static int rch[stopIm];
static int lch[stopIm];

static int cntfr;

static int prevX;//=LCNST;
static int numLC;
static int numRC;
static int prevX_R;//=LCNST;

static float fiducialTLx = 819.0f;
static float fiducialTLy = 574.0f;
static float fiducialBLx = 680.0f;
static float fiducialBLy = 700.0f;
static float fiducialTRx = 1014.0f;
static float fiducialTRy = 574.0f;
static float fiducialBRx = 1267.0f;
static float fiducialBRy = 699.0f;

void setFiducials(float *data) {
    fiducialTLx = data[0];
    fiducialTLy = data[1];
    fiducialBLx = data[2];
    fiducialBLy = data[3];
    fiducialTRx = data[4];
    fiducialTRy = data[5];
    fiducialBRx = data[6];
    fiducialBRy = data[7];
}

void visualize(Mat imageiipm2, float linesL[4], float linesR[4], float left_ldscore, float left_rdscore, float right_ldscore, float right_rdscore) {
	int L1x = linesL[2]+((fiducialBLy-linesL[3])*linesL[0]/linesL[1]);
	int L2x = linesL[2]+((fiducialTLy-linesL[3])*linesL[0]/linesL[1]);
	int R1x = linesR[2]+((fiducialBLy-linesR[3])*linesR[0]/linesR[1]);
	int R2x = linesR[2]+((fiducialTLy-linesR[3])*linesR[0]/linesR[1]);

	line(imageiipm2,Point(L2x,fiducialTLy),Point(L1x,fiducialBLy),Scalar(255,0,0),2);

	line(imageiipm2,Point(R2x,fiducialTLy),Point(R1x,fiducialBLy),Scalar(255,0,0),2);

	char str[128];
	sprintf(str, "[%.3f, %.3f, %.3f, %.3f][%.3f, %.3f, %.3f, %.3f]",
		linesL[0], linesL[1], linesL[2], linesL[3], linesR[0], linesR[1], linesR[2], linesR[3]);
	printf("%s\n", str);

	namedWindow( "Display window curved road", CV_WINDOW_AUTOSIZE );// Create a window for display.
	imshow( "Display window curved road",imageiipm2); 
	waitKey(10);
}



int main( int argc, char** argv ) 
{
	int i,j,k;
	int templd = laneinitfunc();


	char filename[250];
	char outfilename[250];
	char outvideoname[250];
	char outfilenameIPM[250];
	char outvideonameIPM[250];


	
	cv::Size S1 = Size(1920,1080);
	char folderpath[250];
	
	sprintf(folderpath,"/data/local/tmp/frames"); // Name of folder where input frames are present

	int cntframe=0; // frame count
	Mat inputImage,outputImage; // outputImage is final output with lanes marked
	
	int rdflag=1; // When read-flag is 0, no frames are read

	while(rdflag)
	{	
		if((cntframe>=startIm)&&(cntframe<stopIm))
		{
			char inputfname[200];
			cout<<"Frame number:"<<cntframe<<"  ";
			sprintf(inputfname,"%s/frame%d.jpg",folderpath,cntframe);
		
			inputImage = imread(inputfname,CV_LOAD_IMAGE_COLOR);
			Mat grayScale;
			cvtColor(inputImage, grayScale, CV_BGR2GRAY);
			outputImage = inputImage.clone();

			vector<LaneDetectionResults *> *laneDetectionResults = lanedetectAlgo(grayScale,100,0,0,cntframe,0);

			visualize(outputImage, 
				laneDetectionResults->at(0)->line, laneDetectionResults->at(1)->line,
				laneDetectionResults->at(0)->ldscore, laneDetectionResults->at(0)->rdscore,
				laneDetectionResults->at(1)->ldscore, laneDetectionResults->at(1)->rdscore);
		
			
		}
		
		cntframe++;

		if(cntframe>=stopIm)
			rdflag=0;
	}

	return 0;
}


int laneinitfunc(){
	checkval=1000;
	numRC=0;
	numLC=0;;
	cntfr=0;

	prevX=LCNST;

	prevX_R=RCNST;

	
	for(int ii=0;ii<NSCAN;ii++)
	{
		for(int jj=0;jj<N_PREV;jj++)
		{
			laneptsL[ii][jj] = LCNST;//150;
			laneptsR[ii][jj] = RCNST;//220;

			laneptsL_ld[ii][jj] = LCNST-DRIFT;//150;
			laneptsR_ld[ii][jj] = RCNST-DRIFT;//220;

			laneptsL_rd[ii][jj] = LCNST+DRIFT;//150;
			laneptsR_rd[ii][jj] = RCNST+DRIFT;//220;
		}
	}

	for(int ii=0;ii<stopIm;ii++)
	{
		contiL[ii]=0;
		contiR[ii]=0;
	}
	return 0;
}

vector<LaneDetectionResults *> *lanedetectAlgo(Mat &imageiipm1, int cntframesld,double velval, double yawval, int iiii, int vt)
{
	double delta=0;
	int borderType;
	int height,width,step,channels;
	uchar *data;
	int i,j,k;
	int cntframes=0;

	cntfr++;
	checkval++;
	Mat imageiipm2 = imageiipm1;

	
	// Modified steerable filters - Mat version
	Mat gxxmat = (Mat_<float>(1,5) << -0.2707,-0.6065,0,0.6065,0.2707);
	Mat gxymat = (Mat_<float>(5,1) <<0.1353,0.6065,1.0000,0.6065,0.1353);
	
	Size S = Size(1920,1080);

	// If no data in input image, return input as output image
	if(!imageiipm2.data )
	{
		return 0;
	}

	Mat image1 = imageiipm1.clone();

	Mat image = image1;
	Mat imageiipm = imageiipm2;
	Point2f src[4], dst[4];

	// Source coordinates in ego view of image
	src[0].x = fiducialBLx; // bottom left
	src[0].y = fiducialBLy;
	src[1].x = fiducialBRx; // bottom right
	src[1].y = fiducialBRy;
	src[2].x = fiducialTLx; // top left
	src[2].y = fiducialTLy;
	src[3].x = fiducialTRx; // top right
	src[3].y = fiducialTRy;


	// Destination coordinates in bird's eye view of image
	dst[0].x = 150;
	dst[0].y = 360;
	dst[1].x = 150+WID;
	dst[1].y = 360;
	dst[2].x = 150;
	dst[2].y = 1;
	dst[3].x = 150+WID;
	dst[3].y = 1;

	Mat homography(3,3,CV_32F); // Initialization of homography matrix
	homography = getPerspectiveTransform(src, dst); // Generation of homography matrix
	Mat homoInv = homography.inv(); // Generation of inverse homography matrix

	Mat imageipm,imageipmres; 
	warpPerspective(image, imageipm, homography, Size(500,362)); // Applying homography matrix on ego image to generate IPM image
	imageipmres=imageipm;

	Mat Ix;

	sepFilter2D(imageipm,Ix,CV_32FC1,gxxmat,gxymat); // Applying filter on entire image

	Mat J180 = Ix; // 180 is because the filtered output carries only features with gradient angle of 180 degrees
	Mat J180thm = (J180 < -20); // -20 and 20 are the thresholds
	Mat J180thp = (J180 > 20);



	// namedWindow( "IPM2", CV_WINDOW_AUTOSIZE );// Create a window for display.
	// imshow( "IPM2",imageipm);     

	// imwrite("testIPM.png",imageipm);

	int npts[NSCAN];
	Point pts1[NPTSVAL * NSCAN];
	

	int yaxisind[NSCAN] = {120,140,150,160,180,200,210,220,230,240,260,270,280,300,310,330}; // Y-coordinates of bands in the IPM domain
	
	int yst1;
	for(int ijk=0;ijk<NSCAN;ijk++)
	{
		yst1=yaxisind[ijk];
		npts[ijk] = getLanePts(J180thp, J180thm, yst1,yst1+10,0,499);

		for(int kk=0;kk<NPTSVAL;kk++)
		{
			pts1[kk+ijk*NPTSVAL].x = finalPeakPts[kk].x;
			pts1[kk+ijk*NPTSVAL].y = yst1;
		}

		for(int iii=0;iii<=npts[ijk];iii++)
		{
			finalPeakPts[iii].x=0;
			finalPeakPts[iii].y=0;
		}


	}


////%%%%

	Point ptsimage1[NPTSVAL * NSCAN];
	Point ptsimage2[NPTSVAL * NSCAN];
	Point ptsiipm1[NPTSVAL * NSCAN];
	Point ptsiipm2[NPTSVAL * NSCAN];
	int cntpi=0;
	int flag11=0;




	// Kalman filtering based tracking


	// Visualization second logic
	
	int lineproparray[NSCAN] = {0,0,0,0};
	Point ptsimage3[NPTSVAL * NSCAN];
	Point ptsimageld[NSCAN];
	Point ptsimagerd[NSCAN];
	int cntscanpts;
	//int cntim3=-1;
	int maxVal1, minVal1,maxVal1y,minVal1y;
	int minValdrift;
	int flag234=0;
	int flag234_cnt=0;

	// RAVI - COMMENTS
	// Now given the lane points in the location of lane feature points (where peaks occure) in pts1 array, 
	// lets find out which of feature points in each band are valid ones.
	// in order to do this, we check how many of these feature points in each band are located within a delta_x (minVal1)
	// from ideal positions (or expected positions) of lane markers. These expected positions are in xaxisind. They are set to LCNST and RCNST
 	// for left and right lane markers respectively
	int xaxisind[2] = {LCNST,RCNST};
	int xaxisind_rd[2] = {LCNST-DRIFT,RCNST-DRIFT};
	int xaxisind_ld[2] = {LCNST+DRIFT,RCNST+DRIFT};

	int flag234_ld=0;
	int flag234_rd=0;

	int cntLanePts_L=0;
	int cntLanePts_R=0;

	float diff_ld = 0.0;
	float diff_rd = 0.0;

	for(int ii=0;ii<NSCAN;ii++) // lets loop through each band 0<= ii < NSCAN.. this loop is for the left lane marker
	{
		int flag234=0; // set a flag234 to zero. This will be set to 1 if we find a valid lane marker within the bounds from xaxisind
		if(npts[ii]>=0) // npts stores the number of lane feature points that were detected. This is non zero if lane features are detected
		{
			minVal1=10; // initialize the minimum tolerance to 20
			minValdrift=10;
			for(int jj=0;jj<=npts[ii];jj++) // loop through npts to find the valid lane features now for left lane
			{
				if(pts1[ii*NPTSVAL+jj].x != 0) // important indexing here -- ii*NPTSVAL+jj will help to access the jj-th (jj lies between 0 and NPTSVAL =8)
												// lane feature in the ii-th band	
				{
					if(abs(xaxisind[0]-pts1[ii*NPTSVAL+jj].x)<minVal1) // check whether the position fo the detected lane feature from xaxisind[0] (for left lane)
																		// is less than minimum tolerance
					{
						flag234=1; // the flag is set if we find atleast on feature point within the minimum tolerance
						cntLanePts_L++;
						minVal1 = abs(laneptsL[ii][N_PREV-1]-pts1[ii*NPTSVAL+jj].x); // minimum tolerance is reset .. but this line is optional
						ptsimage3[ii].x = pts1[ii*NPTSVAL+jj].x; // ptsimage3 is an array contains the left valid lane positions in the IPM domain 
						ptsimage3[ii].y = pts1[ii*NPTSVAL+jj].y;
						

					}
					else
					{
						if(ii>DRIFT_BANDS)
						{
							if(abs(xaxisind_ld[0]-pts1[ii*NPTSVAL+jj].x)<minValdrift)
							{
								flag234_ld+=1;
								ptsimageld[ii].x = pts1[ii*NPTSVAL+jj].x; 
								ptsimageld[ii].y = pts1[ii*NPTSVAL+jj].y;
								diff_ld+=(abs(xaxisind_ld[0]-pts1[ii*NPTSVAL+jj].x));
							}

							if(abs(xaxisind_rd[0]-pts1[ii*NPTSVAL+jj].x)<minValdrift)
							{
								flag234_rd+=1;
								ptsimagerd[ii].x = pts1[ii*NPTSVAL+jj].x; 
								ptsimagerd[ii].y = pts1[ii*NPTSVAL+jj].y;
								diff_rd+=(abs(xaxisind_rd[0]-pts1[ii*NPTSVAL+jj].x));
							}
						}
					}
				}
			}
		}

		// now lets create a buffer. laneptsL is initialized with LCNST. Now push the last element [0] and pop in the newest lane features position if available
		for(int jj=0;jj<=N_PREV-2;jj++)
		{
			laneptsL[ii][jj] = laneptsL[ii][jj+1];
		}
		if(flag234==0)
			laneptsL[ii][N_PREV-1] = laneptsL[ii][N_PREV-2];
		else
			laneptsL[ii][N_PREV-1] = ptsimage3[ii].x;
	
		// now use the buffer to calculate the mean value for the lane position in ii-th band
		float sumval=0;

		for(int kk=0;kk<N_PREV;kk++)
		{
			sumval+=laneptsL[ii][kk];
		}

		// assign the average to the lane position in ptsimage3 array
		ptsimage3[ii].x=floor(sumval/N_PREV);
		ptsimage3[ii].y=yaxisind[ii];


		// This is the drift buffer	

		if(ii>DRIFT_BANDS)
		{
			for(int jj=0;jj<=N_PREV-2;jj++)
			{
				laneptsL_ld[ii][jj] = laneptsL_ld[ii][jj+1];
				laneptsL_rd[ii][jj] = laneptsL_rd[ii][jj+1];
			}
			if(flag234_ld==0)
				laneptsL_ld[ii][N_PREV-1] = laneptsL_ld[ii][N_PREV-2];
			else
				laneptsL_ld[ii][N_PREV-1] = ptsimageld[ii].x;

			if(flag234_rd==0)
				laneptsL_rd[ii][N_PREV-1] = laneptsL_rd[ii][N_PREV-2];
			else
				laneptsL_rd[ii][N_PREV-1] = ptsimagerd[ii].x;
		
			// now use the buffer to calculate the mean value for the lane position in ii-th band
			float sumval_ld=0;
			float sumval_rd=0;

			for(int kk=0;kk<N_PREV;kk++)
			{
				sumval_ld+=laneptsL_ld[ii][kk];
				sumval_rd+=laneptsL_rd[ii][kk];
			}

			// assign the average to the lane position in ptsimage3 array
			ptsimageld[ii].x=floor(sumval_ld/N_PREV);
			// laneptsL[ii][N_PREV-1] = ptsimage3[ii].x;
			ptsimageld[ii].y=yaxisind[ii];

			// assign the average to the lane position in ptsimage3 array
			ptsimagerd[ii].x=floor(sumval_rd/N_PREV);
			// laneptsL[ii][N_PREV-1] = ptsimage3[ii].x;
			ptsimagerd[ii].y=yaxisind[ii];

		}
		

	}

	float diff_ld_score = 0.0;
	float diff_rd_score = 0.0;

	if(flag234_ld>0){
		diff_ld_score = diff_ld/flag234_ld;
		printf("Left drfit = %f\t%d\n",diff_ld_score,flag234_ld);
	}
	else{
		printf("Left drfit = %f\t%d\n",diff_ld_score,flag234_ld);
	}

	if(flag234_rd>0){
		diff_rd_score = diff_rd/flag234_rd;
		printf("Right drfit = %f\t%d\n",diff_rd_score,flag234_rd);
	}
	else{
		printf("Right drfit = %f\t%d\n",diff_rd_score,flag234_rd);
	}

	char ld_str[100];
	char rd_str[100];

	sprintf(ld_str,"Left drift score = %0.2f",diff_ld_score);
	sprintf(rd_str,"Right drift score = %0.2f",diff_rd_score);
	//printf("%f\t%f\t%d\t%d\n",diff_ldflag234_ld,flag234_rd);

	// Right lane -- the same steps as above are applied to the right lane also

	Point ptsimage3_R[NPTSVAL * NSCAN];
	int flag234_R=0;
	int flag234_R_cnt=0;
	for(int ii=0;ii<NSCAN;ii++)
	{
		int flag234_R=0;
		if(npts[ii]>=0)
		{
			minVal1=10;
			for(int jj=0;jj<=npts[ii];jj++)
			{
				if(pts1[ii*NPTSVAL+jj].x != 0)
				{
					if(abs(xaxisind[1]-pts1[ii*NPTSVAL+jj].x)<minVal1)
						//if(abs(laneptsR[ii][4]-pts1[ii*NPTSVAL+jj].x)<minVal1)
					{
						flag234_R=1;
						cntLanePts_R++;
						minVal1 = abs(laneptsR[ii][4]-pts1[ii*NPTSVAL+jj].x);
						ptsimage3_R[ii].x = pts1[ii*NPTSVAL+jj].x;
						ptsimage3_R[ii].y = pts1[ii*NPTSVAL+jj].y;		
					}

					

				}
			}
		}

		
		for(int jj=0;jj<=N_PREV-2;jj++)
		{
			laneptsR[ii][jj] = laneptsR[ii][jj+1];
		}
		if(flag234_R==0)
			laneptsR[ii][N_PREV-1] = laneptsR[ii][N_PREV-2];
		else
			laneptsR[ii][N_PREV-1] = ptsimage3_R[ii].x;
		
		

		float sumval=0;
		for(int kk=0;kk<N_PREV;kk++)
		{
			sumval+=laneptsR[ii][kk];
		}

		ptsimage3_R[ii].x=floor(sumval/N_PREV);// see how to put prev value;
		ptsimage3_R[ii].y=yaxisind[ii];
		
		
	}

	int sumleft11=0;
	int cntleft11=0;
	Point temp11;
	Point temp22;
	int ptsL11[NSCAN][2];
	int ptsR11[NSCAN][2];

	//Mat ptsiipmL1 = Mat::zeros(NSCAN, 2, CV_8UC1);
	//Mat ptsiipmR1 = Mat::zeros(NSCAN, 2, CV_8UC1);
	// jsv Mat ptsiipmL_1 = Mat(NSCAN,1, CV_16SC1, cvScalar(99));
	// jsv Mat ptsiipmL_2 = Mat(NSCAN,1, CV_16SC1, cvScalar(99));
	// jsv this is different Mat ptsiipmL1 = Mat(NSCAN,2, CV_16SC1, cvScalar(99));;
	Mat ptsiipmL_1 = Mat(NSCAN,1, CV_32SC1, cvScalar(99));
	Mat ptsiipmL_2 = Mat(NSCAN,1, CV_32SC1, cvScalar(99));
	Mat ptsiipmL1 = Mat(NSCAN,2, CV_32SC1, cvScalar(99));;


	//  now starts the visualization
	for(int ii=0;ii<NSCAN;ii++)
	{
		Point tempipm = ptsimage3[ii];
		if(ii==0)
			temp11=tempipm;

		if(ii==NSCAN-1)
			temp22=tempipm;

		ptsiipmL[ii] = getIIPMPt(homoInv,ptsimage3[ii]);
		ptsiipmL_2.at<int>(ii,0) = ptsiipmL[ii].x;
		ptsiipmL_1.at<int>(ii,0) = ptsiipmL[ii].y;

		
		sumleft11+=ptsiipmL[ii].x;
		cntleft11++;
		circle(imageipm, tempipm, 5, CV_RGB(255,255,255), 2, 8, 0);

		/*if(iiii>=10)
		{
		for(int jj=0;jj<N_PREV;jj++)
		{
		laneptsL[ii][jj] = laneptsL[ii][jj+1];
		}
		laneptsL[ii][N_PREV-1] = ptsimage3[ii].x;
		}*/
	}
	hconcat(ptsiipmL_2,ptsiipmL_1,ptsiipmL1);
	// Mat ptsiipmL1 = Mat(NSCAN, 2, CV_16SC1, &ptsL11);


	int sumright11=0;
	int cntright11=0;
	Mat ptsiipmR_1 = Mat(NSCAN,1, CV_32SC1, cvScalar(99));
	Mat ptsiipmR_2 = Mat(NSCAN,1, CV_32SC1, cvScalar(99));
	Mat ptsiipmR1 = Mat(NSCAN,2, CV_32SC1, cvScalar(99));;

	for(int ii=0;ii<NSCAN;ii++)
	{
		
		Point tempipm = ptsimage3_R[ii];
		if(ii==0)
			temp11=tempipm;

		if(ii==NSCAN-1)
			temp22=tempipm;

		ptsiipmR[ii] = getIIPMPt(homoInv,ptsimage3_R[ii]);
		ptsiipmR_2.at<int>(ii,0) = ptsiipmR[ii].x;
		ptsiipmR_1.at<int>(ii,0) = ptsiipmR[ii].y;

		sumright11+=ptsiipmR[ii].x;
		cntright11++;

		circle(imageipm, tempipm, 5, CV_RGB(255,255,255), 2, 8, 0);
	}

	hconcat(ptsiipmR_2,ptsiipmR_1,ptsiipmR1);

	// VISUALIZATION on IMAGE DOMAIN
	Vec4f linesL;
	Vec4f linesR;
	fitLine(ptsiipmL1,linesL,2,0,0.01,0.01);
	fitLine(ptsiipmR1,linesR,2,0,0.01,0.01);

	vector<LaneDetectionResults *> *results = new vector<LaneDetectionResults *>;
	LaneDetectionResults *laneL = new LaneDetectionResults, *laneR = new LaneDetectionResults;
	laneL->line[0] = linesL[0];
	laneL->line[1] = linesL[1];
	laneL->line[2] = linesL[2];
	laneL->line[3] = linesL[3];
	laneL->ldscore = diff_ld_score;
	laneL->rdscore = diff_rd_score;
	results->push_back(laneL);
	laneR->line[0] = linesR[0];
	laneR->line[1] = linesR[1];
	laneR->line[2] = linesR[2];
	laneR->line[3] = linesR[3];
	laneR->ldscore = 0;
	laneR->rdscore = 0;
	results->push_back(laneR);
	return results;

	// REDUNTANT OLD CODE

	// Vec4f sumRvec;
	// Vec4f sumLvec;

	// for(int ivec=1;ivec<N_PREV;ivec++)
	// {
	// 	lanesLBuff[ivec-1]=lanesLBuff[ivec];
	
	// 	lanesRBuff[ivec-1]=lanesRBuff[ivec];
	// 	sumLvec = sumLvec + lanesLBuff[ivec-1];
	// 	sumRvec = sumRvec + lanesRBuff[ivec-1];
	// }
	// lanesLBuff[N_PREV-1]=linesL;
	// lanesRBuff[N_PREV-1]=linesR;

}




Point getIIPMPt(Mat homoVal,Point IPMpt)
{
	Mat temp1 = Mat::zeros(3,3,CV_64F);
	temp1.at<double>(0,0) = IPMpt.x;
	temp1.at<double>(1,0) = IPMpt.y;
	temp1.at<double>(2,0) = 1;
	Mat temp2(3,3,CV_64F);
	temp2 = homoVal * temp1;
	double xxx = temp2.at<double>(0,0)/temp2.at<double>(2,0);
	double yyy = temp2.at<double>(1,0)/temp2.at<double>(2,0);
	Point IIPMpt;
	IIPMpt.x = cvRound(xxx);
	IIPMpt.y = cvRound(yyy);
	return IIPMpt;
}


int getLanePts(Mat bwP, Mat bwM, int i1,int i2,int j1, int j2)
{
	// function that identifies all the lane candidates by performing the following steps:
	// (1) summation of filtered and thresholded output values in every column
	// (2) shift and multiply followed by peak detection
	//
	// Inputs: 
	// Mat bwP, bwM: +ve (Plus) and -ve (Minus) images after filtering and thresholding (binary with 0 or 255 hence bw) 
	// i1, i2: y bounds of the band with respect to image
	// j1, j2: x bounds of the band with respect to image (variable because of IPM)
	// 
	// Outputs:
	// nPt1: number of lane candicates identifies
	
	Mat stripP = bwP(Range(i1,i2),Range(j1,j2)); // extracting band from +ve image
	Mat stripM = bwM(Range(i1,i2),Range(j1,j2)); // extracting band from -ve image
	
	// converting +ve band to 1/0 format from 255/0 format
	Mat stripPb;
	stripP.convertTo(stripPb,CV_32F);
	stripPb = stripPb/255;

	// converting -ve band to 1/0 format from 255/0 format
	Mat stripMb;
	stripM.convertTo(stripMb,CV_32F);
	stripMb = stripMb/255;

	// initialize sum arrays
	Mat pSum = Mat::zeros(1,stripPb.cols,CV_32F);
	Mat mSum = Mat::zeros(1,stripMb.cols,CV_32F);

	// collapse all rows into 1 row by adding them to each other 
	for(int i=0;i<stripPb.rows;i++)
	{
		add(stripPb.row(i),pSum,pSum);
		add(stripMb.row(i),mSum,mSum);
	}

	int X_SHIFT = 4;
	// Multiply the two added arrays after shifting one of them by shift
	Mat stMul;
	multiply(pSum.colRange(Range(0,495)),mSum.colRange(Range(X_SHIFT,495+X_SHIFT)),stMul);

	int nPt1=findPeak(stMul);

	Point Pt1[8];
	Point Pt1sorted[8];

	int cntsort=0;
	if(nPt1>=0)
	{
		for(int i=0;i<=nPt1;i++)
		{
			Pt1[i].x=peaksListPt1[i].x;
			int minVal=1000;
			int indminVal=0;
			for(int j=0;j<=nPt1;j++)
			{
				if(peaksListPt1[j].x<minVal)
				{
					minVal=peaksListPt1[j].x;
					indminVal = j;
				}
			}
			peaksListPt1[indminVal].x=1000;
			Pt1sorted[i].x = minVal;
		}
	}

	int cntdiff=1;
	int diffx;
	int flagentered=0;

	if(nPt1>0)
	{
		for(int i=0;i<=nPt1;i++)
		{
			if(Pt1sorted[i].x>182)
			{
				if(flagentered==0)
				{
					for(int j=i-1;j>=0;j--)
					{
						diffx = Pt1sorted[i].x-Pt1sorted[j].x;

						if((diffx >= (cntdiff*WID-10))&&(diffx <= (cntdiff*WID+10)))
						{
							finalPeakPts[cntdiff-1].x=Pt1sorted[j].x;
							finalPeakPts[cntdiff-1].y=Pt1sorted[j].y;
							cntdiff=cntdiff+1;
						}
						else
						{
						}
					}
					finalPeakPts[cntdiff-1]=Pt1sorted[i];
					cntdiff=cntdiff+1;
					flagentered=1;
				}
				else
				{
					diffx = Pt1sorted[i].x-finalPeakPts[cntdiff-1].x;
					if((diffx > (WID-10))&&(diffx < (WID+10)))
					{
						finalPeakPts[cntdiff-1].x=Pt1sorted[i].x;
						finalPeakPts[cntdiff-1].y=Pt1sorted[i].y;
						cntdiff=cntdiff+1;
					}
				}
			}
		}

		//When we dont find peaks in the lower half, check for upper half
		if(cntdiff==1)
		{
			for(int j=nPt1;j>=0;j--)
			{
				diffx = Pt1sorted[j].x-Pt1sorted[j-1].x;

				if((diffx > (cntdiff*WID-10))&&(diffx < (cntdiff*WID+10)))
				{
					finalPeakPts[cntdiff-1].x=Pt1sorted[j].x;
					finalPeakPts[cntdiff-1].y=Pt1sorted[j].y;
					cntdiff=cntdiff+1;
				}
				else
				{
				}
			}
		}
	}
	else
	{
		if(nPt1==0)
		{
			finalPeakPts[cntdiff-1]=Pt1sorted[0];
		}

	}
	return nPt1;
}



int findPeak(Mat inparray)
{ 
	// given an array, function returns the position of the peaks
	double minVal; double maxVal; Point minLoc; Point maxLoc;
	Point matchLoc;
	int s0,s1;
	int flag=0;
	int cnt=0;
	int MAX_NUM_PEAKS = 8;
	int THRESH = 20;

	while(flag==0)
	{
		minMaxLoc(inparray, &minVal, &maxVal, &minLoc, &maxLoc, Mat() );
		if((maxVal> THRESH)&&(cnt<MAX_NUM_PEAKS))
		{
			peaksListPt1[cnt] = maxLoc; // peaksListPt1 is a global array
			s0 = maxLoc.x-3; // 
			s1 = maxLoc.x+3;
			if(s0<0)
				s0=0;

			if(s1>inparray.cols-1)
				s1=inparray.cols-1;

			inparray.colRange(Range(s0,s1))=Scalar(0); // suppresses max peak, and 3 values on either side to 0
			cnt=cnt+1;
		}
		else
		{
			flag=1; // enters this loop when max number of peaks are already detected or there are no more peaks > THRESH
		}
	}
	return cnt-1; // returns final count of peaks detected
}

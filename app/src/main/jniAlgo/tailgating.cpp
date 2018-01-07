#include <iostream>
#include <stdio.h>
#include <string>
#include <math.h>
#include <fstream>
#include <vector>
#include <stdlib.h>

// define constants
static int IM_X_MIN = 224.0 / 2;
static int BUFFER_LEN = 3;
static float KAPPA = 1050.0;
static int POS_TH = 100;

#define DEBUG 1

void log(const char *msg);
const int logBuffLength = 128;
char logBuff[logBuffLength];

typedef struct {
	float *tg_score_buffer;
	long *timestamps_buffer;
	int tg_score_buffer_size;
} TailgatingScore;

static TailgatingScore tail_gating_score;

float compute_tailgating_score(int bbox[4][4], float speed, int num_bboxes);
int find_last_ts_idx(long);
void tail_gating_init(int dnnImageWidth, int dnnImageHeight);

float getKappa() {
    return KAPPA;
}

void setKappa(float kappa) {
    KAPPA = kappa;
}

float getPosThreshold() {
    return POS_TH;
}

void setPosThreshold(int posThreshold) {
    POS_TH = posThreshold;
}

int find_last_ts_idx(long timestamp_curr_frame)
{
	int last_idx=BUFFER_LEN;

	for(int i=BUFFER_LEN-1;i>=0;i--) {
		if((timestamp_curr_frame-tail_gating_score.timestamps_buffer[i])>2000)
			break;
		else
			last_idx = i;
	}
	return last_idx;
}

void tail_gating_destroy() {
    delete [] tail_gating_score.tg_score_buffer;
    delete [] tail_gating_score.timestamps_buffer;
}

void tail_gating_init(int dnnImageWidth, int dnnImageHeight, int bufferLength) {
	if (bufferLength == 0) {
	    bufferLength = 3;
	}
	tail_gating_score.tg_score_buffer_size = bufferLength;
	tail_gating_score.tg_score_buffer = new float [tail_gating_score.tg_score_buffer_size];
	tail_gating_score.timestamps_buffer = new long [tail_gating_score.tg_score_buffer_size];
	BUFFER_LEN = bufferLength;
	IM_X_MIN = dnnImageWidth / 2.0;
	for(int i=0;i<BUFFER_LEN;++i) {
		tail_gating_score.tg_score_buffer[i] = 0;
		tail_gating_score.timestamps_buffer[i] = 0;
	}
}

float compute_tailgating_score(float bbox[][4], float speed, int num_bboxes)
{
	int loop_bbox=0;
	float tg_score=0.0;
	float dist_est=0.0;
	int diff_pos_min=1920;

	for(loop_bbox=0;loop_bbox<num_bboxes;loop_bbox++){
		int diff_pos = abs(((bbox[loop_bbox][0]+bbox[loop_bbox][2])/2)-IM_X_MIN);
#if DEBUG
		snprintf(logBuff, logBuffLength, "loop_bbox = %d. diff_pos = %d", loop_bbox, diff_pos);
		log(logBuff);
#endif
		if((diff_pos<POS_TH)&&(diff_pos<diff_pos_min)){
			dist_est = KAPPA/(bbox[loop_bbox][2]-bbox[loop_bbox][0]);
			tg_score = speed/dist_est;
			diff_pos_min = diff_pos;
#if DEBUG
    		snprintf(logBuff, logBuffLength, "dist_est = %f. tg_score = %f. diff_pos_min = %d", dist_est, tg_score, diff_pos_min);
    		log(logBuff);
#endif
		}
	}

	return tg_score;
}

typedef float BoundingBox[4];

float executeTailgating(float *boxes, int numberOfBoxes, float speed, long timestamp_current) {
	int ts_idx = find_last_ts_idx(timestamp_current);
	float tg_score_val = compute_tailgating_score((BoundingBox *)boxes, speed, numberOfBoxes);
	float sum_val = 0.0;
    for(int i=1;i<BUFFER_LEN;i++){
        tail_gating_score.tg_score_buffer[i-1]=tail_gating_score.tg_score_buffer[i];
        if(i>=ts_idx){
            sum_val+=tail_gating_score.tg_score_buffer[i-1];
        }
    }
    tail_gating_score.tg_score_buffer[BUFFER_LEN-1]=tg_score_val;
    float ave_tg_score = (sum_val+tg_score_val)/(BUFFER_LEN-ts_idx+1);
    return ave_tg_score;
}

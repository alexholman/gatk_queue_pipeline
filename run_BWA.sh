APPS_PATH=/ifs/labs/cccb/projects/cccb/apps
BWA=$APPS_PATH/bwa_current/bwa

REFERENCE=$1
REFERENCE='/cccbstore-rc/projects/db/gatk/hg19/ucsc.hg19.fasta'
#REFERENCE='/cccbstore-rc/projects/cccb/indecis/Homo_sapiens/UCSC/hg19/Sequence/BWAIndex/genome.fa'

# -R STR	Complete read group header line. 
#			’\t’ can be used in STR and will be converted to a TAB in the output SAM. 
#			The read group ID will be attached to every read in the output. 
#			An example is ’@RG\tID:foo\tSM:bar’. [null]
RG=$( head -n1 $2 )

FASTQFILEA=$3
FASTQFILEB=$4
SAMPLE_DIR=$(dirname $FASTQFILEA)
SAMPLE_NAME=$5

mkdir -p $SAMPLE_DIR/tmp
TMP=$SAMPLE_DIR/tmp

OUTFILE=aligned.bam

if [ -n "$FASTQFILEB" ]
	then PAIRED=1
	else PAIRED=0
fi
NUM0=0
NUM1=1

LOG=$SAMPLE_NAME.bwa.log
date > $LOG
echo "-----" > $LOG
echo "REFERENCE: $REFERENCE" | tee -a $LOG
echo "RG Header: $RG" | tee -a $LOG
echo "FASTQFILEA: $FASTQFILEA" | tee -a $LOG
echo "FASTQFILEB: $FASTQFILEB" | tee -a $LOG
echo "PAIRED: $PAIRED" | tee -a $LOG

mkdir -p $SAMPLE_DIR/bwa

# Align with BWA and hand to samtools to convert to BAM
echo "Start BWA alignment..." | tee -a $LOG; date >> $LOG; echo "-----" >> $LOG
$BWA mem -t 5 -R $RG $REFERENCE $FASTQFILEA $FASTQFILEB | samtools view -Shb - > $SAMPLE_NAME.bam 2>> $LOG

# Index the BAM file. Done here as an intermediate because it should speed up ReorderSam below
echo "Start index BAM..." | tee -a $LOG; date >> $LOG; echo "-----" >> $LOG
java -Xmx4g -jar $APPS_PATH/picard-tools_current/BuildBamIndex.jar \
  INPUT=$SAMPLE_NAME.bam \
  TMP_DIR=$TMP \
  >> $LOG

# Order the BAM file by contigs in the reference
echo "Start order by contigs..." | tee -a $LOG; date >> $LOG; echo "-----" >> $LOG
java -Xmx4g -jar $APPS_PATH/picard-tools_current/ReorderSam.jar \
  INPUT=$SAMPLE_NAME.bam \
  OUTPUT=$TMP/$SAMPLE_NAME.bam \
  REFERENCE=$REFERENCE \
  TMP_DIR=$TMP \
  >> $LOG && mv -f $TMP/$SAMPLE_NAME.bam $SAMPLE_NAME.bam

# Sort the BAM by coordinates
echo "Start coordinate sort..." | tee -a $LOG; date >> $LOG; echo "-----" >> $LOG
java -Xmx4g -jar $APPS_PATH/picard-tools_current/SortSam.jar \
  INPUT=$SAMPLE_NAME.bam \
  OUTPUT=$TMP/$SAMPLE_NAME.bam \
  SORT_ORDER=coordinate \
  TMP_DIR=$TMP \
  >> $LOG && mv -f $TMP/$SAMPLE_NAME.bam $SAMPLE_NAME.bam

# Do a final index of the BAM
echo "Start final index..." | tee -a $LOG; date >> $LOG; echo "-----" >> $LOG
java -Xmx4g -jar $APPS_PATH/picard-tools_current/BuildBamIndex.jar \
  INPUT=$SAMPLE_NAME.bam \
  TMP_DIR=$TMP \
  >> $LOG

echo "-----" >> $LOG
date >> $LOG

#$OUTFILE
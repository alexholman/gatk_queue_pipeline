#APPS_PATH=/ifs/labs/cccb/projects/cccb/apps
REFERENCE=/ifs/labs/cccb/projects/db/gatk/hg19/ucsc.hg19.fasta

INBAM=$1
SAMPLENAME=$(basename $INBAM | sed -e 's/.bam//')
#LOG=run_realign_BAM_$SAMPLENAME.log
echo "begin"

echo "----------"; date; echo "----------";
echo "Splitting $SAMPLENAME..."
mkdir -p $SAMPLENAME
$APPS_PATH/bamUtil_current/bin/bam splitBam --in $INBAM --out $SAMPLENAME/$SAMPLENAME.split -v

SPLITBAMS=$(find $SAMPLENAME/ -maxdepth 1 -name "$SAMPLENAME.split.*.bam")

while read -r SPLITBAM; do
	echo "----------"; date; echo "----------";
	echo "Sorting $SPLITBAM"
	SPLITBAM_BASE=$(echo $SPLITBAM | sed -e 's/.bam//')
	SPLITBAM_SORTED=$SPLITBAM_BASE.sorted.bam
	samtools sort $SPLITBAM $SPLITBAM_BASE.sorted

	echo "----------"; date; echo "----------";
	echo "Converting $SPLITBAM_SORTED to FASTQ"
	$APPS_PATH/bamUtil_current/bin/bam bam2FastQ --in $SPLITBAM_SORTED --merge

	HEADER=$(samtools view -H $SPLITBAM_SORTED | grep @RG | sed -r -e 's/\t/\\t/g')
	FASTQ=$(echo $SPLITBAM_SORTED | sed -e 's/.bam/_interleaved.fastq/')
	BAM_OUT=$(echo $SPLITBAM_SORTED | sed -e 's/.bam//').hg19.bam

	echo "----------"; date; echo "----------";
	echo "Aligning $FASTQ"
	$APPS_PATH/bwa_current/bwa mem -t 4 -p -R $HEADER $REFERENCE $FASTQ | samtools view -Shb - > $BAM_OUT
# exit
	echo "----------"; date; echo "----------";
	echo "Validating $BAM_OUT"
	java -Xmx2g -jar $APPS_PATH/picard-tools_current/ValidateSamFile.jar \
      INPUT=$BAM_OUT \
      OUTPUT=$BAM_OUT.validate

#	rm $FASTQ
#	rm $SPLITBAM
#	rm $SPLITBAM_SORTED
done <<< "$SPLITBAMS"


OUT_COMBINED_BAM=$SAMPLENAME.hg19.bam
echo "----------"; date; echo "----------";
echo "Combining to $OUT_COMBINED_BAM"

mkdir -p $SAMPLENAME/tmp
INPUT=$( find $SAMPLENAME/ -maxdepth 2 -name "*.hg19.bam" | sed -e 's/^/INPUT=/' -e 's/$/ /' )

java -Xmx6g -jar $APPS_PATH/picard-tools_current/MergeSamFiles.jar \
  $INPUT \
  OUTPUT=$OUT_COMBINED_BAM \
  SORT_ORDER=coordinate \
  ASSUME_SORTED=FALSE \
  USE_THREADING=TRUE \
  CREATE_INDEX=TRUE \
  CREATE_MD5_FILE=TRUE \
  TMP_DIR=$SAMPLENAME/tmp

chmod 555 $OUT_COMBINED_BAM
# find $SAMPLENAME/ -maxdepth 2 -name "*.hg19.bam" | xargs -n1 rm 

echo "----------"; date; echo "----------";
echo "Sorting and indexing $OUT_COMBINED_BAM"

OUT_COMBINED_BAM_BASE=$(echo $OUT_COMBINED_BAM | sed -e 's/.bam//')
samtools sort $OUT_COMBINED_BAM $OUT_COMBINED_BAM_BASE.sorted
samtools index $OUT_COMBINED_BAM_BASE.sorted.bam

echo "----------"; date; echo "----------";
echo "Validating $OUT_COMBINED_BAM_BASE.sorted.bam"
java -Xmx2g -jar $APPS_PATH/picard-tools_current/ValidateSamFile.jar \
  INPUT=$OUT_COMBINED_BAM_BASE.sorted.bam \
  OUTPUT=$SAMPLENAME/$OUT_COMBINED_BAM_BASE.sorted.bam.validate







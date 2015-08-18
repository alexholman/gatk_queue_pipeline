APPS_PATH=/data/cccb/apps
DB_PATH=/data/cccb/db
JAVA_PATH=$APPS_PATH/java_current/bin

BAMS_LIST=$1
REGIONS=$2
PED=$3

LOG=run_queue.$(basename $BAMS_LIST .txt).log

$JAVA_PATH/java -jar $APPS_PATH/Queue_current/Queue.jar \
  -S $APPS_PATH/gatk_queue_pipeline/ExomeGATKPipeline.scala \
  --dbsnp $DB_PATH/gatk/hg19/dbsnp_137.hg19.vcf \
  --reference $DB_PATH/gatk/hg19/ucsc.hg19.fasta \
  -L $REGIONS \
  --input $BAMS_LIST \
  --hapmap $DB_PATH/gatk/hg19/hapmap_3.3.hg19.vcf \
  --omni $DB_PATH/gatk/hg19/1000G_omni2.5.hg19.vcf \
  --thousandGenomes $DB_PATH/gatk/hg19/1000G_phase1.snps.high_confidence.hg19.vcf \
  --mills $DB_PATH/gatk/hg19/Mills_and_1000G_gold_standard.indels.hg19.vcf \
  --dbsnpvqsr $DB_PATH/gatk/hg19/dbsnp_137.hg19.vcf \
  --snpEff_path $APPS_PATH/snpEff_current/ \
  --snpEff_genome hg19 \
  --graphviz graph_out.gv \
  --graphviz_scatter_gather sg_graph_out.gv \
  --pedigree $PED \
  --num_threads 1 \
  --scatter_gather 50 \
  -jobRunner Lsf706 \
  -retry 3 \
  -jobQueue medium \
  --complete_run false \
  -run \
>> $LOG 2>&1


#  -startFromScratch \
#  --input bams_list.txt \
#  -L two_region_list.list \


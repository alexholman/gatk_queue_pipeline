APPS_PATH=/ifs/labs/cccb/projects/cccb/apps
DB_PATH=/ifs/labs/cccb/projects/db
INPUT_BAMS=$1
REGIONS=$2

java -jar $APPS_PATH/Queue_current/Queue.jar \
  -S $APPS_PATH/gatk_queue_pipeline/ExomeGATKPipeline.scala \
  --dbsnp $DB_PATH/gatk/hg19/dbsnp_137.hg19.vcf \
  --reference $DB_PATH/gatk/hg19/ucsc.hg19.fasta \
  --input $INPUT_BAMS \
  -L $REGIONS \
  --hapmap $DB_PATH/gatk/hg19/hapmap_3.3.hg19.vcf \
  --omni $DB_PATH/gatk/hg19/1000G_omni2.5.hg19.vcf \
  --thousandGenomes $DB_PATH/gatk/hg19/1000G_phase1.snps.high_confidence.hg19.vcf \
  --mills $DB_PATH/gatk/hg19/Mills_and_1000G_gold_standard.indels.hg19.vcf \
  --dbsnpvqsr $DB_PATH/gatk/hg19/dbsnp_137.hg19.vcf \
  --snpEff_path $APPS_PATH/snpEff_current/ \
  --snpEff_genome hg19 \
  --graphviz graph_out.gv \
  --graphviz_scatter_gather sg_graph_out.gv \
  --num_threads 4 \
  --scatter_gather 10 \
  -jobRunner GridEngine \
  -retry 2 \
  -run

#  -jobQueue medium \
#  -startFromScratch \
#


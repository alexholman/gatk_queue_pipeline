APPS_PATH=/ifs/labs/cccb/projects/cccb/apps
DB_PATH=/ifs/labs/cccb/projects/db

java -jar $APPS_PATH/Queue_current/Queue.jar \
  -S ../gatk_queue_pipeline/DataProcessingPipeline.scala \
  --dbsnp $DB_PATH/gatk/hg19/dbsnp_137.hg19.vcf \
  --reference $DB_PATH/gatk/hg19/ucsc.hg19.fasta \
  -L two_region_list.list \
  --input input_list.txt \
  -jobRunner GridEngine \
  -startFromScratch \
  -run


#  --input data/M_2915_1.contigSort.coordSort.bam \

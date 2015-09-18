package org.broadinstitute.gatk.queue.qscripts

import org.broadinstitute.gatk.queue.extensions.gatk._ 
import org.broadinstitute.gatk.queue.QScript 
import org.broadinstitute.gatk.queue.extensions.picard._
import org.broadinstitute.gatk.tools.walkers._
import org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel
import org.broadinstitute.gatk.utils.baq.BAQ.CalculationMode

import collection.JavaConversions._
import htsjdk.samtools.SAMFileReader
import htsjdk.samtools.SAMFileHeader.SortOrder

import org.broadinstitute.gatk.queue.util.QScriptUtils
import org.broadinstitute.gatk.queue.function.ListWriterFunction 
import org.broadinstitute.gatk.utils.commandline.Hidden
import org.broadinstitute.gatk.utils.commandline 

import org.broadinstitute.gatk.queue.extensions.snpeff._

//import org.broadinstitute.gatk.tools.walkers.haplotypecaller.HaplotypeCaller
//import org.broadinstitute.gatk.tools.walkers.haplotypecaller.HaplotypeCaller.ReferenceConfidenceMode.GVCF

class DataProcessingPipeline extends QScript {
  qscript =>

  /****************************************************************************
  * Required Parameters
  ****************************************************************************/


  @Input(doc="input BAM file - or list of BAM files", fullName="input", shortName="i", required=true)
  var input: File = _

  @Input(doc="Reference fasta file", fullName="reference", shortName="R", required=true)
  var reference: File = _

  @Input(doc="dbsnp ROD to use (must be in VCF format)", fullName="dbsnp", shortName="D", required=true)
  var dbSNP: Seq[File] = Seq()

  @Input(doc="dbsnp file for VariantAnnotator and VQSR training", fullName="dbsnpvqsr", shortName="dbsnpvqsr", required=true)
  var dbSNPvqsr: File = _

  @Input(doc="hapmap training set for VQSR", fullName="hapmap", shortName="hapmap", required=true)
  var hapmap: File = _

  @Input(doc="omni training set for VQSR", fullName="omni", shortName="omni", required=true)
  var omni: File= _

  @Input(doc="1000genomes training set for VQSR", fullName="thousandGenomes", shortName="thousandGenomes", required=true)
  var thousandGenomes: File = _

  @Input(doc="Mills and 1000genomes gold standard indels", fullName="mills", shortName="mills", required=true)
  var mills: File = _

  @Argument(doc="Path to snpEff directory", fullName="snpEff_path", shortName="snpEff_path", required=true)
  var snpEff_path: String = _



  /****************************************************************************
  * Optional Parameters
  ****************************************************************************/

  @Input(doc="extra VCF files to use as reference indels for Indel Realignment", fullName="extra_indels", shortName="indels", required=false)
  var indels: Seq[File] = Seq()

  @Input(doc="The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName="path_to_bwa", shortName="bwa", required=false)
  var bwaPath: File = _

  @Argument(doc="the project name determines the final output (BAM file) base name. Example NA12878 yields NA12878.processed.bam", fullName="project", shortName="p", required=false)
  var projectName: String = "project"

  @Argument(doc="Output path for the processed BAM files.", fullName="output_directory", shortName="outputDir", required=false)
  var outputDir: String = ""

  @Argument(doc="the -L interval string to be used by GATK - output bams at interval only", fullName="gatk_interval_string", shortName="L", required=false)
  var intervalString: String = ""

  @Input(doc="an intervals file to be used by GATK - output bams at intervals only", fullName="gatk_interval_file", shortName="intervals", required=false)
  var intervals: File = _

  @Argument(doc="Cleaning model: KNOWNS_ONLY, USE_READS or USE_SW", fullName="clean_model", shortName="cm", required=false)
  var cleaningModel: String = "USE_READS"

  @Argument(doc="Decompose input BAM file and fully realign it using BWA and assume Single Ended reads", fullName="use_bwa_single_ended", shortName="bwase", required=false)
  var useBWAse: Boolean = false

  @Argument(doc="Decompose input BAM file and fully realign it using BWA and assume Pair Ended reads", fullName="use_bwa_pair_ended", shortName="bwape", required=false)
  var useBWApe: Boolean = false

  @Argument(doc="Decompose input BAM file and fully realign it using BWA SW", fullName="use_bwa_sw", shortName="bwasw", required=false)
  var useBWAsw: Boolean = false
/*
  @Argument(doc="Number of threads BWA should use", fullName="bwa_threads", shortName="bt", required=false)
  var bwaThreads: Int = 1
*/
  @Argument(doc="Perform validation on the BAM files", fullName="validation", shortName="vs", required=false)
  var validation: Boolean = false

  @Argument(doc="GenotypeGVCFs: The minimum phred-scaled confidence threshold at which variants should be called", fullName="standard_min_confidence_threshold_for_calling", shortName="stand_call_conf", required=false)
  var stand_call_conf: Double = 30.0

  @Argument(doc="GenotypeGVCFs: The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", fullName="standard_min_confidence_threshold_for_emitting", shortName="stand_emit_conf", required=false)
  var stand_emit_conf: Double = 10.0

  @Argument(doc="GenotypeGVCFs: Maximum number of alternate alleles to genotype", fullName="max_alternate_alleles", shortName="maxAltAlleles", required=false)
  var max_alternate_alleles: Int = 20

  @Argument(doc="nt or ntc for GATK: Number of threads to use", fullName="num_threads", shortName="nt", required=false)
  var num_threads: Int = 1

  @Argument(doc="MarkDuplicates memory limit", fullName="markDuplicates_memLimit", shortName="md_mem", required=false)
  var markDuplicates_memLimit: Int = 4


  @Argument(doc="Global memory limit", fullName="memoryLimit", shortName="memoryLimit", required=false)
  var memoryLimit: Int = 4

  @Argument(doc="snpEff genome to use", fullName="snpEff_genome", shortName="snpEff_genome", required=false)
  var snpEff_genome: String = "hg19"

  @Argument(doc="Run fully through GVCF to VCF creation and VCF recalibration", fullName="complete_run", shortName="complete", required=false)
  var complete_run: String = "true"

  @Input(doc="Pedigree file", fullName="pedigree", shortName="ped", required=false)
  var pedigree: File = _
//  var pedigree: Seq[File] = Seq()

  @Argument(doc="Amount of padding in bp to add to each interval", fullName="interval_padding", shortName="ip", required=false)
  var interval_padding: Int = 100


  /****************************************************************************
  * Hidden Parameters
  ****************************************************************************/
  @Hidden
  @Argument(doc="How many ways to scatter/gather", fullName="scatter_gather", shortName="sg", required=false)
  var nContigs: Int = -1

  @Hidden
  @Argument(doc="Define the default platform for Count Covariates -- useful for techdev purposes only.", fullName="default_platform", shortName="dp", required=false)
  var defaultPlatform: String = ""

  @Hidden
  @Argument(doc="Run the pipeline in test mode only", fullName = "test_mode", shortName = "test", required=false)
  var testMode: Boolean = false

  /****************************************************************************
  * Global Variables
  ****************************************************************************/

  val queueLogDir: String = ".qlog/"  // Gracefully hide Queue's output

  var cleanModelEnum: ConsensusDeterminationModel = ConsensusDeterminationModel.USE_READS

  // Variables for Haplotype Caller
  var GATKVCFIndexType: String = "LINEAR" 



  /****************************************************************************
  * Helper classes and methods
  ****************************************************************************/

  class ReadGroup (val id: String,
                   val lb: String,
                   val pl: String,
                   val pu: String,
                   val sm: String,
                   val cn: String,
                   val ds: String)
  {}


  // Utility function to merge all bam files of similar samples. Generates one BAM file per sample.
  // It uses the sample information on the header of the input BAM files.
  //
  // Because the realignment only happens after these scripts are executed, in case you are using
  // bwa realignment, this function will operate over the original bam files and output over the
  // (to be realigned) bam files.
  def createSampleFiles(bamFiles: Seq[File], realignedBamFiles: Seq[File]): Map[String, Seq[File]] = {

    // Creating a table with SAMPLE information from each input BAM file
    val sampleTable = scala.collection.mutable.Map.empty[String, Seq[File]]
    val realignedIterator = realignedBamFiles.iterator
    for (bam <- bamFiles) {
      val rBam = realignedIterator.next()  // advance to next element in the realignedBam list so they're in sync.

      val samReader = new SAMFileReader(bam)
      val header = samReader.getFileHeader
      val readGroups = header.getReadGroups

      // only allow one sample per file. Bam files with multiple samples would require pre-processing of the file
      // with PrintReads to separate the samples. Tell user to do it himself!
      assert(!QScriptUtils.hasMultipleSamples(readGroups), "The pipeline requires that only one sample is present in a BAM file. Please separate the samples in " + bam)

      // Fill out the sample table with the readgroups in this file
      for (rg <- readGroups) {
        val sample = rg.getSample
        if (!sampleTable.contains(sample))
          sampleTable(sample) = Seq(rBam)
        else if ( !sampleTable(sample).contains(rBam))
          sampleTable(sample) :+= rBam
      }
    }
    sampleTable.toMap
  }



/*
  // Rebuilds the Read Group string to give BWA
  def addReadGroups(inBam: File, outBam: File, samReader: SAMFileReader) {
    val readGroups = samReader.getFileHeader.getReadGroups
    var index: Int = readGroups.length
    for (rg <- readGroups) {
      val intermediateInBam: File = if (index == readGroups.length) { inBam } else { swapExt(outBam, ".bam", index+1 + "-rg.bam") }
      val intermediateOutBam: File = if (index > 1) {swapExt(outBam, ".bam", index + "-rg.bam") } else { outBam}
      val readGroup = new ReadGroup(rg.getReadGroupId, rg.getLibrary, rg.getPlatform, rg.getPlatformUnit, rg.getSample, rg.getSequencingCenter, rg.getDescription)
      add(addReadGroup(intermediateInBam, intermediateOutBam, readGroup))
      index = index - 1
    }
  }
*/

/*
  // Takes a list of processed BAM files and realign them using the BWA option requested  (bwase or bwape).
  // Returns a list of realigned BAM files.
  def performAlignment(bams: Seq[File]): Seq[File] = {
    var realignedBams: Seq[File] = Seq()
    var index = 1
    for (bam <- bams) {
      // first revert the BAM file to the original qualities
      val saiFile1 = swapExt(bam, ".bam", "." + index + ".1.sai")
      val saiFile2 = swapExt(bam, ".bam", "." + index + ".2.sai")
      val realignedSamFile = swapExt(bam, ".bam", "." + index + ".realigned.sam")
      val realignedBamFile = swapExt(bam, ".bam", "." + index + ".realigned.bam")
      val rgRealignedBamFile = swapExt(bam, ".bam", "." + index + ".realigned.rg.bam")

      if (useBWAse) {
        val revertedBAM = revertBAM(bam, true)
        add(bwa_aln_se(revertedBAM, saiFile1),
            bwa_sam_se(revertedBAM, saiFile1, realignedSamFile))
      }
      else if (useBWApe) {
        val revertedBAM = revertBAM(bam, true)
        add(bwa_aln_pe(revertedBAM, saiFile1, 1),
            bwa_aln_pe(revertedBAM, saiFile2, 2),
            bwa_sam_pe(revertedBAM, saiFile1, saiFile2, realignedSamFile))
      }
      else if (useBWAsw) {
        val revertedBAM = revertBAM(bam, false)
        val fastQ = swapExt(revertedBAM, ".bam", ".fq")
        add(convertToFastQ(revertedBAM, fastQ),
            bwa_sw(fastQ, realignedSamFile))
      }
      add(sortSam(realignedSamFile, realignedBamFile, SortOrder.coordinate))
      addReadGroups(realignedBamFile, rgRealignedBamFile, new SAMFileReader(bam))
      realignedBams :+= rgRealignedBamFile
      index = index + 1
    }
    realignedBams
  }
*/

  def getIndelCleaningModel: ConsensusDeterminationModel = {
    if (cleaningModel == "KNOWNS_ONLY")
      ConsensusDeterminationModel.KNOWNS_ONLY
    else if (cleaningModel == "USE_SW")
      ConsensusDeterminationModel.USE_SW
    else
      ConsensusDeterminationModel.USE_READS
  }

/*
  def revertBams(bams: Seq[File], removeAlignmentInformation: Boolean): Seq[File] = {
    var revertedBAMList: Seq[File] = Seq()
    for (bam <- bams)
      revertedBAMList :+= revertBAM(bam, removeAlignmentInformation)
    revertedBAMList
  }

  def revertBAM(bam: File, removeAlignmentInformation: Boolean): File = {
    val revertedBAM = swapExt(bam, ".bam", ".reverted.bam")
    add(revert(bam, revertedBAM, removeAlignmentInformation))
    revertedBAM
  }
*/

  /****************************************************************************
  * Main script
  ****************************************************************************/


  def script() {
    // final output list of processed bam files
    var cohortList: Seq[File] = Seq()
    var gVCFlist: Seq[File] = Seq()

    // sets the model for the Indel Realigner
    cleanModelEnum = getIndelCleaningModel

    // keep a record of the number of contigs in the first bam file in the list
    val bams = QScriptUtils.createSeqFromFile(input)
    if (nContigs < 0)
     nContigs = QScriptUtils.getNumberOfContigs(bams(0))

//  trying to strip out the unneeded realignment steps
//    val realignedBAMs = if (useBWApe || useBWAse  || useBWAsw) {performAlignment(bams)} else {revertBams(bams, false)}
    val realignedBAMs = bams

    // generate a BAM file per sample joining all per lane files if necessary
    val sampleBAMFiles: Map[String, Seq[File]] = createSampleFiles(bams, realignedBAMs)

    // if this is a 'knowns only' indel realignment run, do it only once for all samples.
    val globalIntervals = new File(outputDir + projectName + ".intervals")
    if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY)
      add(target(null, globalIntervals))

    // put each sample through the pipeline
    for ((sample, bamList) <- sampleBAMFiles) {

      // BAM files generated by the pipeline
      val bam        = new File(qscript.projectName + "." + sample + ".bam")
      val cleanedBam = swapExt(bam, ".bam", ".clean.bam")
      val dedupedBam = swapExt(bam, ".bam", ".clean.dedup.bam")

// With Dedup
      val recalBam   = swapExt(bam, ".bam", ".clean.dedup.recal.bam")
// Without Dedup
//      val recalBam   = swapExt(bam, ".bam", ".clean.recal.bam")

      // GVCF files
      val outGVCF    = swapExt(bam, ".bam", ".g.vcf")

      // Accessory files
      val targetIntervals = if (cleaningModel == ConsensusDeterminationModel.KNOWNS_ONLY) {globalIntervals} else {swapExt(bam, ".bam", ".intervals")}
      val metricsFile     = swapExt(bam, ".bam", ".metrics")
      val preRecalFile    = swapExt(bam, ".bam", ".pre_recal.table")
      val postRecalFile   = swapExt(bam, ".bam", ".post_recal.table")
      val preOutPath      = swapExt(bam, ".bam", ".pre")
      val postOutPath     = swapExt(bam, ".bam", ".post")
      val preValidateLog  = swapExt(bam, ".bam", ".pre.validation")
      val postValidateLog = swapExt(bam, ".bam", ".post.validation")


      // Validation is an optional step for the BAM file generated after
      // alignment and the final bam file of the pipeline.
      if (validation) {
        for (sampleFile <- bamList)
        add(validate(sampleFile, preValidateLog),
            validate(recalBam, postValidateLog))
      }

      if (cleaningModel != ConsensusDeterminationModel.KNOWNS_ONLY)
        add(target(bamList, targetIntervals))

// Without Dedup
/*
      add(clean(bamList, targetIntervals, cleanedBam),
          cov(cleanedBam, preRecalFile),
          recal(cleanedBam, preRecalFile, recalBam),
          cov(recalBam, postRecalFile),
          hapcall(recalBam, outGVCF)
         )
*/

// With Dedup
      add(clean(bamList, targetIntervals, cleanedBam),
          dedup(cleanedBam, dedupedBam, metricsFile),
          cov(dedupedBam, preRecalFile),
          recal(dedupedBam, preRecalFile, recalBam),
          cov(recalBam, postRecalFile),
          hapcall(recalBam, outGVCF)
         )



      cohortList :+= recalBam
      gVCFlist :+= outGVCF
    } // close for each BAM



	// Now run the post processing steps after the GVCF stage
	if (complete_run=="true"){

		// VCF files
		val combinedGVCF		= qscript.outputDir + qscript.projectName + ".g.vcf"
		val rawVCF				= qscript.outputDir + qscript.projectName + ".vcf"
		var combinedGVCFlist: Seq[File] = Seq()
		combinedGVCFlist :+= combinedGVCF
	
	//	val snpEffVCF			= swapExt(rawVCF, ".vcf", ".snpEff.vcf")
		val VCFvarAnnotate		= swapExt(rawVCF, ".vcf", ".varAnn.vcf")

		val SNPrecalVCF			= swapExt(VCFvarAnnotate, ".vcf", ".snpRecal.vcf")
		val SNPrecalRecal		= swapExt(SNPrecalVCF, ".vcf", ".recal")
		val SNPrecalTranches	= swapExt(SNPrecalVCF, ".vcf", ".tranches")
		val SNPrecalRPlots		= swapExt(SNPrecalVCF, ".vcf", ".plots.R")

		val INDELrecalVCF			= swapExt(SNPrecalVCF, ".vcf", ".indelRecal.vcf")
		val INDELrecalRecal			= swapExt(INDELrecalVCF, ".vcf", ".recal")
		val INDELrecalTranches		= swapExt(INDELrecalVCF, ".vcf", ".tranches")
		val INDELrecalRPlots		= swapExt(INDELrecalVCF, ".vcf", ".plots.R")

		val FilteredVCF				= swapExt(INDELrecalVCF, ".vcf", ".filtered.vcf")

		add( 
			combineGVCFs(gVCFlist, combinedGVCF),
			genotypeGVCFs(combinedGVCFlist, rawVCF),
			varannotator(rawVCF, VCFvarAnnotate),
			VQSRsnp(VCFvarAnnotate, SNPrecalRecal, SNPrecalTranches, SNPrecalRPlots),
			applyRecalSNP(VCFvarAnnotate, SNPrecalTranches, SNPrecalRecal, 99.9, SNPrecalVCF),
			VQSRindel(SNPrecalVCF, INDELrecalRecal, INDELrecalTranches, INDELrecalRPlots),
			applyRecalINDEL(SNPrecalVCF, INDELrecalTranches, INDELrecalRecal, 99.0, INDELrecalVCF),
			selectFilterPass(INDELrecalVCF, FilteredVCF)
		)


	    // output a BAM list with all the processed per sample files
	    val cohortFile = new File(qscript.outputDir + qscript.projectName + ".cohort.list")
	    add(writeList(cohortList, cohortFile))
	}
  }



  /****************************************************************************
  * Classes (GATK Walkers)
  ****************************************************************************/



  // General arguments to non-GATK tools
  trait ExternalCommonArgs extends CommandLineFunction {
    this.memoryLimit = memoryLimit
    this.isIntermediate = true
  }

  // General arguments to GATK walkers
  trait CommandLineGATKArgs extends CommandLineGATK with ExternalCommonArgs {
    this.reference_sequence = qscript.reference
    this.memoryLimit = memoryLimit
    this.num_threads = num_threads
    this.num_cpu_threads_per_data_thread = num_threads
    this.interval_padding = interval_padding
//    this.pedigree = pedigree
    this.pedigree :+= pedigree
//    this.pedigree = List(pedigree)
    this.pedigreeValidationType = org.broadinstitute.gatk.engine.samples.PedigreeValidationType.SILENT
  }

  trait SAMargs extends PicardBamFunction with ExternalCommonArgs {
      this.maxRecordsInRam = 100000
  }

  case class target (inBams: Seq[File], outIntervals: File) extends RealignerTargetCreator with CommandLineGATKArgs {
    if (cleanModelEnum != ConsensusDeterminationModel.KNOWNS_ONLY)
      this.input_file = inBams
    this.out = outIntervals
    this.mismatchFraction = 0.0
    this.known ++= qscript.dbSNP
    if (indels != null)
      this.known ++= qscript.indels
    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outIntervals + ".target"
    this.jobName = queueLogDir + outIntervals + ".target"
  }

  case class clean (inBams: Seq[File], tIntervals: File, outBam: File) extends IndelRealigner with CommandLineGATKArgs {
    this.input_file = inBams
    this.targetIntervals = tIntervals
    this.out = outBam
    this.known ++= qscript.dbSNP
    if (qscript.indels != null)
      this.known ++= qscript.indels
    this.consensusDeterminationModel = cleanModelEnum
    this.compress = 0
    this.noPGTag = qscript.testMode;
    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outBam + ".clean"
    this.jobName = queueLogDir + outBam + ".clean"
    this.isIntermediate = true
  }

  case class cov (inBam: File, outRecalFile: File) extends BaseRecalibrator with CommandLineGATKArgs {
    this.knownSites ++= qscript.dbSNP
    this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
    this.input_file :+= inBam
    this.disable_indel_quals = true
    this.out = outRecalFile
    if (!defaultPlatform.isEmpty) this.default_platform = defaultPlatform
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals
    this.scatterCount = nContigs
    this.analysisName = queueLogDir + outRecalFile + ".covariates"
    this.jobName = queueLogDir + outRecalFile + ".covariates"
  }

  case class recal (inBam: File, inRecalFile: File, outBam: File) extends PrintReads with CommandLineGATKArgs {
    this.input_file :+= inBam
    this.BQSR = inRecalFile
    this.baq = CalculationMode.CALCULATE_AS_NECESSARY
    this.out = outBam
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals
    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outBam + ".recalibration"
    this.jobName = queueLogDir + outBam + ".recalibration"
  }

  case class hapcall (inBam: File, outGVCF: File) extends HaplotypeCaller with CommandLineGATKArgs {
    this.input_file :+= inBam
    this.out = outGVCF
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.emitRefConfidence = org.broadinstitute.gatk.tools.walkers.haplotypecaller.ReferenceConfidenceMode.GVCF
    this.variant_index_type = org.broadinstitute.gatk.utils.variant.GATKVCFIndexType.LINEAR
    this.variant_index_parameter = variant_index_parameter
    this.variant_index_parameter = Some(128000)

    // this.standard_min_confidence_threshold_for_calling = qscript.stand_call_conf
    // this.standard_min_confidence_threshold_for_emitting = qscript.stand_emit_conf

    this.num_cpu_threads_per_data_thread = num_threads
    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outGVCF + ".HaplotypeCaller"
    this.jobName = queueLogDir + outGVCF + ".HaplotypeCaller"
  }

  case class combineGVCFs (inGVCFs: Seq[File], outGVCF: File) extends CombineGVCFs with CommandLineGATKArgs {
    this.variant = inGVCFs
    this.out = outGVCF
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    // this.memoryLimit = 2
    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outGVCF + ".CombineGVCFs"
    this.jobName = queueLogDir + outGVCF + ".CombineGVCFs"

  }


  case class genotypeGVCFs (inGVCF: Seq[File], outVCF: File) extends GenotypeGVCFs with CommandLineGATKArgs {
    this.variant = inGVCF
    this.out = outVCF
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.dbsnp = qscript.dbSNP.last
    this.stand_call_conf = stand_call_conf
    this.stand_emit_conf = stand_emit_conf
    this.max_alternate_alleles = max_alternate_alleles
    this.annotateNDA = true

    // this.memoryLimit = 2
    this.scatterCount = nContigs
    this.isIntermediate = true
    this.analysisName = queueLogDir + outVCF + ".GenotypeGVCFs"
    this.jobName = queueLogDir + outVCF + ".GenotypeGVCFs"
  }


  case class varannotator (inVcf: File, outVcf: File) extends VariantAnnotator with CommandLineGATKArgs  {
    this.variant = inVcf
//    this.snpEffFile = inSnpEffFile
    this.out = outVcf
    this.alwaysAppendDbsnpId = true
    this.dbsnp = dbSNPvqsr
    this.R = reference
//    this.annotation = Seq("GenotypeSummaries", "VariantType")
    this.annotation = Seq("GenotypeSummaries", "VariantType", "InbreedingCoeff", "TransmissionDisequilibriumTest", "PossibleDeNovo")
    this.isIntermediate = false
    this.analysisName = queueLogDir + outVcf + ".varannotator"
    this.jobName = queueLogDir + outVcf + ".varannotator"
    this.scatterCount = nContigs
  }


  case class VQSRsnp (inVCF: File, outRecal: File, outTranches: File, outRscript: File) extends VariantRecalibrator with CommandLineGATKArgs {
    this.input :+= inVCF
    this.mode = variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.recal_file = outRecal
    this.tranches_file = outTranches
    this.rscript_file = outRscript
    this.pedigree = pedigree
    this.tranche ++= List("100.0", "99.9", "99.0", "90.0")
    this.resource :+= new TaggedFile(hapmap, "known=false,training=true,truth=true,prior=15.0")
    this.resource :+= new TaggedFile(omni, "known=false,training=true,truth=true,prior=12.0")
    this.resource :+= new TaggedFile(thousandGenomes, "known=false,training=true,truth=false,prior=10.0")
    this.resource :+= new TaggedFile(dbSNPvqsr, "known=true,training=false,truth=false,prior=2.0")
    this.use_annotation ++= List("QD", "MQRankSum", "ReadPosRankSum", "FS", "InbreedingCoeff")
    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

//    this.scatterCount = nContigs
//    this.nt = gatkOptions.nbrOfThreads
    this.isIntermediate = false
    this.analysisName = queueLogDir + ".VQSRsnp"
    this.jobName = queueLogDir + ".VQSRsnp"
  }

  case class VQSRindel (inVCF: File, outRecal: File, outTranches: File, outRscript: File) extends VariantRecalibrator with CommandLineGATKArgs {
    this.input :+= inVCF
    this.mode = variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    this.recal_file = outRecal
    this.tranches_file = outTranches
    this.rscript_file = outRscript

    this.tranche ++= List("100.0", "99.9", "99.0", "90.0")
    this.resource :+= new TaggedFile(mills, "known=false,training=true,truth=true,prior=12.0")
    this.resource :+= new TaggedFile(dbSNPvqsr, "known=true,training=false,truth=false,prior=2.0")
    this.use_annotation ++= List("QD", "ReadPosRankSum", "FS", "InbreedingCoeff")
    this.maxGaussians = 4

    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

//    this.scatterCount = nContigs
//    this.nt = gatkOptions.nbrOfThreads
    this.isIntermediate = false
    this.analysisName = queueLogDir + ".VQSRsnp"
    this.jobName = queueLogDir + ".VQSRsnp"
  }


  case class applyRecalSNP (inVCF: File, inTranches: File, inRecal: File, tsFilterLevel: Double, outVCF: File) extends ApplyRecalibration with CommandLineGATKArgs {
    this.input :+= inVCF
    this.recal_file = inRecal
    this.tranches_file = inTranches
    this.ts_filter_level = tsFilterLevel
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.SNP
    this.out = outVCF

    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outVCF + "ApplyRecal_" + mode
    this.jobName = queueLogDir + outVCF + "ApplyRecal_" + mode
  }

  case class applyRecalINDEL (inVCF: File, inTranches: File, inRecal: File, tsFilterLevel: Double, outVCF: File) extends ApplyRecalibration with CommandLineGATKArgs {
    this.input :+= inVCF
    this.recal_file = inRecal
    this.tranches_file = inTranches
    this.ts_filter_level = tsFilterLevel
    this.mode = org.broadinstitute.gatk.tools.walkers.variantrecalibration.VariantRecalibratorArgumentCollection.Mode.INDEL
    this.out = outVCF

    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outVCF + "ApplyRecal_" + mode
    this.jobName = queueLogDir + outVCF + "ApplyRecal_" + mode
  }

  case class selectFilterPass (inVCF: File, outVCF: File) extends SelectVariants with CommandLineGATKArgs {
    this.variant = inVCF
    this.excludeFiltered = true
    this.out = outVCF

    if (!qscript.intervalString.isEmpty) this.intervalsString ++= Seq(qscript.intervalString)
    else if (qscript.intervals != null) this.intervals :+= qscript.intervals

    this.scatterCount = nContigs
    this.isIntermediate = false
    this.analysisName = queueLogDir + outVCF + "SelectFilterPass_"
    this.jobName = queueLogDir + outVCF + "SelectFilterPass_"
  }




  /****************************************************************************
   * Classes (non-GATK programs)
   ****************************************************************************/


  case class dedup (inBam: File, outBam: File, metricsFile: File) extends MarkDuplicates with ExternalCommonArgs {
    this.input :+= inBam
    this.output = outBam
    this.metrics = metricsFile
    this.memoryLimit = markDuplicates_memLimit
    this.analysisName = queueLogDir + outBam + ".dedup"
    this.jobName = queueLogDir + outBam + ".dedup"
    this.isIntermediate = true
  }

  case class joinBams (inBams: Seq[File], outBam: File) extends MergeSamFiles with ExternalCommonArgs {
    this.input = inBams
    this.output = outBam
    this.analysisName = queueLogDir + outBam + ".joinBams"
    this.jobName = queueLogDir + outBam + ".joinBams"
  }

  case class sortSam (inSam: File, outBam: File, sortOrderP: SortOrder) extends SortSam with ExternalCommonArgs {
    this.input :+= inSam
    this.output = outBam
    this.sortOrder = sortOrderP
    this.analysisName = queueLogDir + outBam + ".sortSam"
    this.jobName = queueLogDir + outBam + ".sortSam"
  }

  case class validate (inBam: File, outLog: File) extends ValidateSamFile with ExternalCommonArgs {
    this.input :+= inBam
    this.output = outLog
    this.REFERENCE_SEQUENCE = qscript.reference
    this.isIntermediate = false
    this.analysisName = queueLogDir + outLog + ".validate"
    this.jobName = queueLogDir + outLog + ".validate"
  }



  case class addReadGroup (inBam: File, outBam: File, readGroup: ReadGroup) extends AddOrReplaceReadGroups with ExternalCommonArgs {
    this.input :+= inBam
    this.output = outBam
    this.RGID = readGroup.id
    this.RGCN = readGroup.cn
    this.RGDS = readGroup.ds
    this.RGLB = readGroup.lb
    this.RGPL = readGroup.pl
    this.RGPU = readGroup.pu
    this.RGSM = readGroup.sm
    this.analysisName = queueLogDir + outBam + ".rg"
    this.jobName = queueLogDir + outBam + ".rg"
  }

/*
  case class revert (inBam: File, outBam: File, removeAlignmentInfo: Boolean) extends RevertSam with ExternalCommonArgs {
    this.output = outBam
    this.input :+= inBam
    this.removeAlignmentInformation = removeAlignmentInfo;
    this.sortOrder = if (removeAlignmentInfo) {SortOrder.queryname} else {SortOrder.coordinate}
    this.analysisName = queueLogDir + outBam + "revert"
    this.jobName = queueLogDir + outBam + ".revert"
  }
*/

/*
  case class convertToFastQ (inBam: File, outFQ: File) extends SamToFastq with ExternalCommonArgs {
    this.input :+= inBam
    this.fastq = outFQ
    this.analysisName = queueLogDir + outFQ + "convert_to_fastq"
    this.jobName = queueLogDir + outFQ + ".convert_to_fastq"
  }
*/

/*
  case class bwa_aln_se (inBam: File, outSai: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc="bam file to be aligned") var bam = inBam
    @Output(doc="output sai file") var sai = outSai
    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + reference + " -b " + bam + " > " + sai
    this.analysisName = queueLogDir + outSai + ".bwa_aln_se"
    this.jobName = queueLogDir + outSai + ".bwa_aln_se"
  }

  case class bwa_aln_pe (inBam: File, outSai1: File, index: Int) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc="bam file to be aligned") var bam = inBam
    @Output(doc="output sai file for 1st mating pair") var sai = outSai1
    def commandLine = bwaPath + " aln -t " + bwaThreads + " -q 5 " + reference + " -b" + index + " " + bam + " > " + sai
    this.analysisName = queueLogDir + outSai1 + ".bwa_aln_pe1"
    this.jobName = queueLogDir + outSai1 + ".bwa_aln_pe1"
  }

  case class bwa_sam_se (inBam: File, inSai: File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc="bam file to be aligned") var bam = inBam
    @Input(doc="bwa alignment index file") var sai = inSai
    @Output(doc="output aligned bam file") var alignedBam = outBam
    def commandLine = bwaPath + " samse " + reference + " " + sai + " " + bam + " > " + alignedBam
    this.memoryLimit = 6
    this.analysisName = queueLogDir + outBam + ".bwa_sam_se"
    this.jobName = queueLogDir + outBam + ".bwa_sam_se"
  }

  case class bwa_sam_pe (inBam: File, inSai1: File, inSai2:File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc="bam file to be aligned") var bam = inBam
    @Input(doc="bwa alignment index file for 1st mating pair") var sai1 = inSai1
    @Input(doc="bwa alignment index file for 2nd mating pair") var sai2 = inSai2
    @Output(doc="output aligned bam file") var alignedBam = outBam
    def commandLine = bwaPath + " sampe " + reference + " " + sai1 + " " + sai2 + " " + bam + " " + bam + " > " + alignedBam
    this.memoryLimit = 6
    this.analysisName = queueLogDir + outBam + ".bwa_sam_pe"
    this.jobName = queueLogDir + outBam + ".bwa_sam_pe"
  }

  case class bwa_sw (inFastQ: File, outBam: File) extends CommandLineFunction with ExternalCommonArgs {
    @Input(doc="fastq file to be aligned") var fq = inFastQ
    @Output(doc="output bam file") var bam = outBam
    def commandLine = bwaPath + " bwasw -t " + bwaThreads + " " + reference + " " + fq + " > " + bam
    this.analysisName = queueLogDir + outBam + ".bwasw"
    this.jobName = queueLogDir + outBam + ".bwasw"
  }
*/

  case class writeList(inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    this.analysisName = queueLogDir + outBamList + ".bamList"
    this.jobName = queueLogDir + outBamList + ".bamList"
  }



/*
  class annotate_snpEff(inVCF: File, outVCF: File) extends CommandLineFunction {
    @Input(doc="vcf to annotate") var inVCF: File = _
    @Output(doc="indexed VCF") 
    
    def commandLine = "myScript.sh hello world"

  } // close class annotate_snpEff



  def annotate_snpEff(inVcf: File, outVCF: File) {
        val eff = new SnpEff
        eff.config = new File(snpEff_path + "/snpEff.config")
        eff.genomeVersion = snpEff_genome

        eff.inVcf = inVcf
        var snpEffout: File = outVCF
        eff.outVcf = swapExt(outVCF, "vcf", "out")

        eff.javaClasspath = List(snpEff_path)
        eff.jarFile = snpEff_path + "/snpEff.jar"

        add(eff)
//        add(varannotator(eff.inVcf,eff.outVcf,snpEffout))
  } // close def annotate_snpEff

// swapExt(eff.inVcf, "vcf", "snpEff.vcf")

*/




}







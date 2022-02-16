/*
 *  ---license-start
 *  eu-digital-green-certificates / dgca-verifier-app-android
 *  ---
 *  Copyright (C) 2021 T-Systems International GmbH and all other contributors
 *  ---
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ---license-end
 */

package it.ministerodellasalute.verificaC19.ui.main.verification

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import it.ministerodellasalute.verificaC19.BuildConfig
import it.ministerodellasalute.verificaC19.R
import it.ministerodellasalute.verificaC19.databinding.FragmentVerificationBinding
import it.ministerodellasalute.verificaC19.ui.base.isDebug
import it.ministerodellasalute.verificaC19.ui.compounds.QuestionCompound
import it.ministerodellasalute.verificaC19sdk.VerificaDownloadInProgressException
import it.ministerodellasalute.verificaC19sdk.VerificaMinSDKVersionException
import it.ministerodellasalute.verificaC19sdk.VerificaMinVersionException
import it.ministerodellasalute.verificaC19sdk.model.*
import it.ministerodellasalute.verificaC19sdk.util.FORMATTED_VALIDATION_DATE
import it.ministerodellasalute.verificaC19sdk.util.TimeUtility.formatDateOfBirth
import it.ministerodellasalute.verificaC19sdk.util.TimeUtility.parseTo
import java.util.*

@ExperimentalUnsignedTypes
@AndroidEntryPoint
class VerificationFragment : Fragment(), View.OnClickListener {

    private val args by navArgs<VerificationFragmentArgs>()
    private val viewModel by viewModels<VerificationViewModel>()

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private lateinit var certificateModel: CertificateViewBean

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener(this)
        viewModel.certificate.observe(viewLifecycleOwner) { certificate ->
            certificate?.let {
                certificateModel = it
                setPersonData(it.person, it.dateOfBirth)
                setupCertStatusView(it)
                setupTimeStamp(it)
                binding.closeButton.visibility = View.VISIBLE
                binding.validationDate.visibility = View.VISIBLE
                if (
                    viewModel.getTotemMode() &&
                    (certificate.certificateStatus == CertificateStatus.VALID)
                ) {
                    Handler().postDelayed({
                        activity?.onBackPressed()
                    }, 5000)
                }
            }
        }
        viewModel.inProgress.observe(viewLifecycleOwner) {
            binding.progressBar.isVisible = it
        }
        try {
            viewModel.init(args.qrCodeText, true)
        } catch (e: VerificaMinSDKVersionException) {
            Log.d("VerificationFragment", "Min SDK Version Exception")
            createForceUpdateDialog(getString(R.string.updateMessage))
        } catch (e: VerificaMinVersionException) {
            Log.d("VerificationFragment", "Min App Version Exception")
            createForceUpdateDialog(getString(R.string.updateMessage))
        } catch (e: VerificaDownloadInProgressException) {
            Log.d("VerificationFragment", "Download In Progress Exception")
            createForceUpdateDialog(getString(R.string.messageDownloadStarted))
        }
    }

    private fun setupCertStatusView(cert: CertificateViewBean) {
        cert.certificateStatus?.let {
            setBackgroundColor(it)
            setPersonDetailsVisibility(it)
            setValidationIcon(it)
            setValidationMainText(it)
            setValidationSubTextVisibility(it)
            setValidationSubText(it)
            setLinkViews(it)
            setScanModeText()
        }
    }

    private fun setScanModeText() {
        val chosenScanMode = when (viewModel.getScanMode()) {
            ScanMode.STANDARD -> getString(R.string.scan_mode_3G_header)
            ScanMode.STRENGTHENED -> getString(R.string.scan_mode_2G_header)
            ScanMode.BOOSTER -> getString(R.string.scan_mode_booster_header)
            ScanMode.SCHOOL -> getString(R.string.scan_mode_school_header)
            ScanMode.WORK -> getString(R.string.scan_mode_work_header)
            ScanMode.ENTRY_ITALY -> getString(R.string.scan_mode_entry_italy_header)
        }
        binding.scanModeText.text = chosenScanMode
    }

    private fun setupTimeStamp(cert: CertificateViewBean) {
        binding.validationDate.text = getString(
            R.string.label_validation_timestamp, cert.timeStamp?.parseTo(
                FORMATTED_VALIDATION_DATE
            )
        )
        binding.validationDate.visibility = View.VISIBLE
    }

    private fun setLinkViews(certStatus: CertificateStatus) {
        binding.questionContainer.removeAllViews()
        val questionMap: Map<String, String> = when (certStatus) {
            CertificateStatus.VALID -> mapOf(getString(R.string.label_what_can_be_done) to BuildConfig.VERIFICATION_FAQ_URL)
            CertificateStatus.NOT_VALID_YET -> mapOf(getString(R.string.label_when_qr_valid) to BuildConfig.VERIFICATION_FAQ_URL)
            CertificateStatus.NOT_VALID, CertificateStatus.EXPIRED, CertificateStatus.REVOKED -> mapOf(
                getString(R.string.label_why_qr_not_valid) to BuildConfig.VERIFICATION_FAQ_URL
            )
            CertificateStatus.TEST_NEEDED -> mapOf(getString(R.string.label_why_is_test_needed) to BuildConfig.VERIFICATION_FAQ_URL)
            CertificateStatus.NOT_EU_DCC -> mapOf(getString(R.string.label_which_qr_scan) to BuildConfig.VERIFICATION_FAQ_URL)
        }
        questionMap.map {
            val compound = QuestionCompound(context)
            compound.setupWithLabels(it.key, it.value)
            binding.questionContainer.addView(compound)
        }
        binding.questionContainer.clipChildren = false
    }

    private fun setValidationSubTextVisibility(certStatus: CertificateStatus) {
        binding.subtitleText.visibility = when (certStatus) {
            CertificateStatus.NOT_EU_DCC -> View.GONE
            else -> View.VISIBLE
        }
    }

    private fun setValidationSubText(certStatus: CertificateStatus) {
        binding.subtitleText.text =
            when (certStatus) {
                CertificateStatus.VALID, CertificateStatus.TEST_NEEDED -> getString(R.string.subtitle_text)
                CertificateStatus.NOT_VALID, CertificateStatus.EXPIRED, CertificateStatus.NOT_VALID_YET -> getString(R.string.subtitle_text_notvalid)
                else -> getString(R.string.subtitle_text_technicalError)
            }
    }

    private fun setValidationMainText(certStatus: CertificateStatus) {
        binding.certificateValid.text = when (certStatus) {
            CertificateStatus.VALID -> getString(R.string.certificateValid)
            CertificateStatus.NOT_EU_DCC -> getString(R.string.certificateNotDCC)
            CertificateStatus.REVOKED -> if (isDebug()) getString(R.string.certificateRevoked) else getString(
                R.string.certificateNonValid
            )
            CertificateStatus.NOT_VALID -> getString(R.string.certificateNonValid)
            CertificateStatus.EXPIRED -> getString(R.string.certificateExpired)
            CertificateStatus.TEST_NEEDED -> getString(R.string.certificateValidTestNeeded)
            CertificateStatus.NOT_VALID_YET -> getString(R.string.certificateNonValidYet)
        }
    }

    private fun setValidationIcon(certStatus: CertificateStatus) {
        binding.checkmark.background =
            ContextCompat.getDrawable(
                requireContext(), when (certStatus) {
                    CertificateStatus.VALID -> R.drawable.ic_valid_cert
                    CertificateStatus.NOT_VALID_YET -> R.drawable.ic_not_valid_yet
                    CertificateStatus.NOT_EU_DCC -> R.drawable.ic_technical_error
                    CertificateStatus.TEST_NEEDED -> R.drawable.ic_warning
                    else -> R.drawable.ic_invalid
                }
            )
    }

    private fun setPersonDetailsVisibility(certStatus: CertificateStatus) {
        binding.containerPersonDetails.visibility = when (certStatus) {
            CertificateStatus.VALID, CertificateStatus.REVOKED, CertificateStatus.TEST_NEEDED, CertificateStatus.NOT_VALID, CertificateStatus.EXPIRED, CertificateStatus.NOT_VALID_YET -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun setBackgroundColor(certStatus: CertificateStatus) {
        binding.verificationBackground.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                when (certStatus) {
                    CertificateStatus.VALID -> R.color.green
                    CertificateStatus.TEST_NEEDED -> R.color.orange
                    else -> R.color.red_bg
                }
            )
        )
    }

    private fun setPersonData(person: PersonModel?, dateOfBirth: String?) {
        binding.nameStandardisedText.text = person?.familyName.plus(" ").plus(person?.givenName)
        binding.birthdateText.text = dateOfBirth?.formatDateOfBirth().orEmpty()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.close_button -> findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun createForceUpdateDialog(message: String) {
        val builder = this.activity?.let { AlertDialog.Builder(requireContext()) }
        builder!!.setTitle(getString(R.string.updateTitle))
        builder.setMessage(message)
        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            findNavController().popBackStack()
        }
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()
    }

}

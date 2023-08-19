package com.mazpiss.adminmedical

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.mazpiss.adminmedical.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var selectedImages = mutableListOf<Uri>()
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        selectImages()

    }

    private fun selectImages() {
        val selectedImagesActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data

                // Multiple images selected
                if (intent?.clipData != null) {
                    val count = intent.clipData?.itemCount ?: 0
                    (0 until count).forEach {
                        val imageUri = intent.clipData?.getItemAt(it)?.uri
                        imageUri?.let {
                            selectedImages.add(it)
                        }
                    }
                } else {
                    val imageUri = intent?.data
                    imageUri?.let { selectedImages.add(it) }
                }
                updateImages()
            }
        }

        binding.btnImg.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectedImagesActivityResult.launch(intent)
        }
    }

    private fun updateImages() {
        binding.tvSelectedImage.text = "Jumlah: ${selectedImages.size.toString()}"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.btn_Upload) {
            val productValidation = validateInformation()
            if (!productValidation) {
                Toast.makeText(this, "Silahkan cek input datanya", Toast.LENGTH_SHORT).show()
                return false
            }
            saveProduct()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun validateInformation(): Boolean {
        if (binding.edtName.text.toString().trim().isEmpty())
            return false

        if (binding.edtDesc.text.toString().trim().isEmpty())
            return false

        if (binding.edtPrice.text.toString().trim().isEmpty())
            return false

        if (binding.edtRules.text.toString().trim().isEmpty())
            return false

        return true
    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArrays = mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if (imageBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                imagesByteArrays.add(stream.toByteArray())
            }
        }
        return imagesByteArrays
    }

    private fun saveProduct() {
        val name = binding.edtName.text.toString().trim()
        val price = binding.edtPrice.text.toString().trim()
        val desc = binding.edtDesc.text.toString().trim()
        val rules = binding.edtRules.text.toString().trim()
        val images = mutableListOf<String>()
        val imagesByteArrays = getImagesByteArrays()

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                showLoading()
            }

            try {
                async {
                    imagesByteArrays.forEach{
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productStorage.child("products/images/$id")
                            val result = imageStorage.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e:Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }

            val product = Product (
                UUID.randomUUID().toString(),
                name,
                price.toInt(),
                desc,
                rules,
                images
            )

            firestore.collection("Products").add(product)
                .addOnSuccessListener {
                    hideLoading()
                    binding.edtName.text?.clear()
                    binding.edtPrice.text?.clear()
                    binding.edtDesc.text?.clear()
                    binding.edtRules.text?.clear()

                    selectedImages.clear()
                    updateImages()
                    Toast.makeText(this@MainActivity, "Barang berhasil di upload", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    hideLoading()
                    Log.e("Error", it.message.toString())
                }
        }
    }

    private fun hideLoading() {
        binding.progessBarInput.visibility = View.GONE
    }

    private fun showLoading() {
        binding.progessBarInput.visibility = View.VISIBLE
    }

}
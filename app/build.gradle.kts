plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.kotlin.compose)
}


android {
    namespace = "com.example.itrialscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.itrialscanner"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.appcompat.v151)
    implementation(libs.androidx.constraintlayout.v221)
    implementation(libs.material)

    // CameraX
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)


    // iText (PDF生成)
    implementation(libs.itextg)

    // 图片加载库
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // RecyclerView
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    implementation(project(":opencv"))

    // ViewPager2 依赖
    implementation(libs.androidx.viewpager2)
    implementation(libs.photoview.v230)

    // 添加TensorFlow Lite依赖
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.8.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.0")


//    // ML Kit对象检测
//    implementation("com.google.mlkit:object-detection:17.0.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
//    // 可选：添加自定义模型支持
//    implementation("com.google.mlkit:object-detection-custom:17.0.0")
//    implementation("com.google.mlkit:vision-common:17.3.0")
//    implementation("com.google.mlkit:text-recognition:16.0.0")

    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // 生命周期扩展
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
}
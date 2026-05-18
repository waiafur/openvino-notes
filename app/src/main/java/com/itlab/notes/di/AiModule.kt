package com.itlab.notes

import com.itlab.ai.OpenVinoEngine
import com.itlab.ai.OpenVinoNoteAiService
import com.itlab.ai.ResultProcessor
import com.itlab.domain.ai.NoteAiService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val aiModule = module {
    single {
        val context = androidContext()
        val modelPath = OpenVinoEngine.getOptimalModelPath(context)
        OpenVinoEngine(
            context = context,
            modelXmlPath = modelPath
        )
    }
    single { ResultProcessor() }
    single<NoteAiService> { OpenVinoNoteAiService(get(), get()) }
}

package com.itlab.ai.di

import com.itlab.ai.OpenVinoEngine
import com.itlab.ai.OpenVinoNoteAiService
import com.itlab.ai.ResultProcessor
import com.itlab.domain.ai.NoteAiService
import org.koin.dsl.module

val aiModule = module {
    single { OpenVinoEngine(fileSystem = get()) }
    single { ResultProcessor() }
    single<NoteAiService> {
        OpenVinoNoteAiService(engine = get(), processor = get())
    }
}

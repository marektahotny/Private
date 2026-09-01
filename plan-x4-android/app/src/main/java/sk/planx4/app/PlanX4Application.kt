package sk.planx4.app

import android.app.Application
import sk.planx4.app.data.ProjectRepository

class PlanX4Application : Application() {
    val projectRepository: ProjectRepository by lazy { ProjectRepository(filesDir) }
}

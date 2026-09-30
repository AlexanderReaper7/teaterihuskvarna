import {defineCliConfig} from 'sanity/cli'
import {dataset, previewOrigin, projectId} from './env'

export default defineCliConfig({
  api: {projectId, dataset},
  deployment: {autoUpdates: true},
  vite: {
    server: {allowedHosts: [new URL(previewOrigin).hostname]},
  },
})

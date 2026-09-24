import {defineConfig} from 'sanity'
import {structureTool} from 'sanity/structure'
import {visionTool} from '@sanity/vision'
import {schemaTypes} from './schemaTypes'

export default defineConfig({
  name: 'default',
  title: 'teater-i-huskvarna',

  projectId: 'gk5ur3tb',
  dataset: 'production',

  plugins: [
    structureTool(),
    visionTool({
      defaultApiVersion: 'v2025-08-19',
      defaultDataset: 'production',
    }),
  ],

  schema: {
    types: schemaTypes,
  },
})

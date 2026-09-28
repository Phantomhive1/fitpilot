import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/requirement' },
  { path: '/requirement', component: () => import('../views/RequirementForm.vue') },
  { path: '/dashboard/:planId?', component: () => import('../views/DashboardView.vue') },
  { path: '/chat', component: () => import('../views/ChatView.vue') },
  { path: '/pose', component: () => import('../views/PoseCheck.vue') }
]

export default createRouter({
  history: createWebHistory(),
  routes
})

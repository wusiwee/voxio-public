<template>
  <error-state code="401" title="这个页面你没有权限"
    hint="当前账号的角色里没有这项授权。需要处理的话，请联系管理员为你的角色开通对应权限，再重新进入。" />
</template>

<script setup>
import ErrorState from '@/components/ErrorState'
</script>

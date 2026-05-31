package com.example.transport_app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.transport_app.data.model.GroupWithCount
import com.example.transport_app.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    val groups: StateFlow<List<GroupWithCount>> = groupRepository.observeGroupsWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveGroupOrder(groups: List<GroupWithCount>) {
        viewModelScope.launch {
            groupRepository.reorderGroups(groups.map { it.id })
        }
    }
}
